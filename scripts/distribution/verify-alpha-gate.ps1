<#
.SYNOPSIS
    Pre-flight and live check for the GitHub Pages alpha portal (SMO-S4-01).

.DESCRIPTION
    Verifies the code-side distribution gate and, when `gh` is authenticated, the repository-side
    configuration. Designed to be run before a release PR merges and again after the first deploy.

    Checks:
      1. the workflow still guards all four ALPHA_* secrets and validates the keystore;
      2. no keystore material is tracked by git;
      3. `gh` is authenticated and the four repository secrets exist;
      4. Pages is configured to deploy from GitHub Actions;
      5. the live portal answers 200 (unless -SkipLive).

.PARAMETER Repo
    GitHub repository in owner/name form. Default smoh-apps/ShowMeOutfit.

.PARAMETER PagesUrl
    Portal URL. Default https://smoh-apps.github.io/ShowMeOutfit/.

.PARAMETER SkipLive
    Skip the network check (useful before the first deployment).

.EXAMPLE
    .\scripts\distribution\verify-alpha-gate.ps1

.NOTES
    Exit codes: 0 = all checks pass, 1 = a check failed, 2 = blocked on `gh` authentication.
#>
[CmdletBinding()]
param(
    [string]$Repo = "smoh-apps/ShowMeOutfit",
    [string]$PagesUrl = "https://smoh-apps.github.io/ShowMeOutfit/",
    [switch]$SkipLive
)

$ErrorActionPreference = "Stop"
$script:Failures = 0
$script:Blocked = $false

function Write-Check {
    param([string]$Name, [bool]$Ok, [string]$Detail = "")
    if ($Ok) {
        Write-Host ("  [PASS] {0}" -f $Name) -ForegroundColor Green
    }
    else {
        Write-Host ("  [FAIL] {0}{1}" -f $Name, $(if ($Detail) { " - $Detail" } else { "" })) -ForegroundColor Red
        $script:Failures++
    }
}

function Write-Skip {
    param([string]$Name, [string]$Detail)
    Write-Host ("  [SKIP] {0} - {1}" -f $Name, $Detail) -ForegroundColor Yellow
}

<#
    Native tools write diagnostics to stderr, and `$ErrorActionPreference = "Stop"` would turn a
    non-zero exit (expected for an unauthenticated gh) into a terminating error. Run native calls
    with stderr captured and return both output and exit code.
#>
function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments)
    $previous = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = & $Exe @Arguments 2>&1 | Out-String
        return [pscustomobject]@{ Output = $output.Trim(); ExitCode = $LASTEXITCODE }
    }
    finally {
        $ErrorActionPreference = $previous
    }
}

Write-Host "ShowMeOutfit distribution gate" -ForegroundColor Cyan
Write-Host "Repository: $Repo"
Write-Host ""

# ---------------------------------------------------------------- 1. workflow
Write-Host "1. Workflow guard"
$workflowPath = ".github/workflows/deploy-pages.yml"
$workflowOk = Test-Path $workflowPath
Write-Check "workflow file exists" $workflowOk $workflowPath

if ($workflowOk) {
    $workflow = Get-Content $workflowPath -Raw
    foreach ($needle in @(
            "ALPHA_KEYSTORE_BASE64",
            "ALPHA_KEYSTORE_PASSWORD",
            "ALPHA_KEY_ALIAS",
            "ALPHA_KEY_PASSWORD",
            "keytool -list",
            "actions/deploy-pages@v4",
            "ALPHA_SIGNING_REQUIRED=true")) {
        Write-Check "workflow contains '$needle'" ($workflow.Contains($needle))
    }
}

# ---------------------------------------------------------------- 2. no keys
Write-Host ""
Write-Host "2. No signing material in git"
$trackedKeys = (Invoke-Native "git" @("ls-files", "*.jks", "*.keystore")).Output
Write-Check "no .jks/.keystore tracked" ([string]::IsNullOrWhiteSpace($trackedKeys)) $trackedKeys

$gitignore = if (Test-Path ".gitignore") { Get-Content ".gitignore" -Raw } else { "" }
Write-Check ".gitignore covers *.jks" $gitignore.Contains("*.jks")
Write-Check ".gitignore covers *.keystore" $gitignore.Contains("*.keystore")

# ---------------------------------------------------------------- 3. gh auth + secrets
Write-Host ""
Write-Host "3. Repository secrets (requires gh authentication)"
$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    $script:Blocked = $true
    Write-Skip "gh CLI" "not installed; install GitHub CLI to verify secrets"
}
else {
    $auth = Invoke-Native "gh" @("auth", "status")
    $authenticated = $auth.ExitCode -eq 0
    if (-not $authenticated) {
        $script:Blocked = $true
        Write-Skip "gh authentication" "run 'gh auth login' and re-run this script"
    }
    else {
        Write-Check "gh is authenticated" $true

        $secretNames = @()
        $secretList = Invoke-Native "gh" @("secret", "list", "--repo", $Repo)
        if ($secretList.ExitCode -ne 0) {
            Write-Check "secret list readable" $false $secretList.Output
        }
        else {
            $secretNames = $secretList.Output -split "`r?`n" |
                Where-Object { $_ -and $_ -notmatch "^\s*$" } |
                ForEach-Object { ($_ -split "`t")[0].Trim() }
        }

        foreach ($name in @(
                "ALPHA_KEYSTORE_BASE64",
                "ALPHA_KEYSTORE_PASSWORD",
                "ALPHA_KEY_ALIAS",
                "ALPHA_KEY_PASSWORD")) {
            Write-Check "secret $name exists" ($secretNames -contains $name)
        }
    }
}

# ---------------------------------------------------------------- 4. Pages source
Write-Host ""
Write-Host "4. Pages source"
if ($script:Blocked) {
    Write-Skip "Pages build_type" "requires authenticated gh"
}
else {
    $buildType = $null
    $pages = Invoke-Native "gh" @("api", "repos/$Repo/pages", "--jq", ".build_type")
    if ($pages.ExitCode -eq 0) {
        $buildType = $pages.Output
    }

    if ([string]::IsNullOrWhiteSpace($buildType)) {
        Write-Skip "Pages build_type" "Pages is not enabled yet - set Source: GitHub Actions (see distribution-gate.md)"
        $script:Blocked = $true
    }
    else {
        Write-Check "Pages build_type is 'workflow'" ($buildType -eq "workflow") "found '$buildType'"
    }
}

# ---------------------------------------------------------------- 5. live URL
Write-Host ""
Write-Host "5. Live portal"
if ($SkipLive) {
    Write-Skip "portal reachable" "-SkipLive was set"
}
else {
    try {
        $response = Invoke-WebRequest -Uri $PagesUrl -Method Head -UseBasicParsing -TimeoutSec 20
        Write-Check "portal answers 200" ($response.StatusCode -eq 200) "status $($response.StatusCode)"
    }
    catch {
        Write-Check "portal answers 200" $false $_.Exception.Message
    }
}

# ---------------------------------------------------------------- verdict
Write-Host ""
if ($script:Failures -gt 0) {
    Write-Host "FAIL: $script:Failures check(s) failed." -ForegroundColor Red
    exit 1
}
if ($script:Blocked) {
    Write-Host "BLOCKED: code-side gate is green; repository-side setup needs an authenticated admin." -ForegroundColor Yellow
    exit 2
}

Write-Host "PASS: the distribution gate is fully configured." -ForegroundColor Green
exit 0
