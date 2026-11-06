# Distribution gate (SMO-S4-01)

This runbook is the **last mile** between a green `sprint-4` and a publicly installable alpha at
`https://smoh-apps.github.io/ShowMeOutfit/`.

The code side is complete: `.github/workflows/deploy-pages.yml` refuses to build without all four
`ALPHA_*` secrets, validates that the decoded keystore opens with the configured alias/password,
publishes `public/showmeoutfit-alpha.apk` and deploys the portal through `actions/deploy-pages@v4`.
Everything that remains needs a human with repository-admin access.

## 1. Preconditions

| Item | Why |
| :--- | :--- |
| Repository admin on `smoh-apps/ShowMeOutfit` | Secrets and Pages settings are admin-only |
| `gh` authenticated (`gh auth status`) | The commands below use it |
| Alpha keystore + passwords | Created once per `alpha-signing.md` |
| `sprint-4` merged to `main` (or a `v*` tag pushed) | The workflow only runs on `main` / `v*` |
| Pages source = **GitHub Actions** | `actions/deploy-pages` cannot publish to a branch source |

## 2. Configure the four secrets

```powershell
gh auth login
gh secret set ALPHA_KEYSTORE_BASE64   --repo smoh-apps/ShowMeOutfit --body (Get-Content alpha-keystore.b64 -Raw)
gh secret set ALPHA_KEYSTORE_PASSWORD --repo smoh-apps/ShowMeOutfit
gh secret set ALPHA_KEY_ALIAS         --repo smoh-apps/ShowMeOutfit
gh secret set ALPHA_KEY_PASSWORD      --repo smoh-apps/ShowMeOutfit
```

`ALPHA_KEYSTORE_BASE64` is the Base64 of the `.jks`; the other three are the values used when the
key was generated. Delete the local `.b64` afterwards. Full key-generation steps:
[`alpha-signing.md`](alpha-signing.md).

## 3. Point Pages at GitHub Actions

```powershell
gh api -X POST repos/smoh-apps/ShowMeOutfit/pages `
  -f build_type=workflow
```

If Pages was previously configured for a branch, use
`gh api -X PUT repos/smoh-apps/ShowMeOutfit/pages -f build_type=workflow`.
The response must report `"build_type": "workflow"`.

## 4. Run the pre-flight

```powershell
.\scripts\distribution\verify-alpha-gate.ps1
```

The script checks, in order:

1. the workflow still contains the four-secret guard, the keystore validation and the deploy job;
2. no keystore is tracked by git;
3. `gh` is authenticated and all four secrets exist;
4. the Pages source is `workflow`;
5. the live portal answers `200` (skipped with `-SkipLive`).

It exits non-zero with the exact failing step, so it can be attached to a release checklist.

## 5. First green deployment

1. Merge the sprint PR (`sprint-4` → `main`) or push a tag: `git tag v1.0.0 && git push origin v1.0.0`.
2. Watch the run: `gh run watch --repo smoh-apps/ShowMeOutfit`.
3. Confirm the three evidence points:
   - the `build` job uploaded `public/` containing `showmeoutfit-alpha.apk` and `index.html`;
   - the `deploy` job reports the `github-pages` environment URL;
   - `verify-alpha-gate.ps1` returns `PASS` with the live URL.
4. Install the APK over the previous alpha **without uninstalling**. This is the stable-signature
   check from SMO-S3-06 and is the one step a browser cannot prove.

## 6. Current status (06 Nov 2026)

| Step | Status |
| :--- | :--- |
| Workflow guard + keystore validation | ✅ Implemented |
| Pre-flight script | ✅ Implemented |
| Four repository secrets | ⛔ **Blocked — `gh auth status` reports "not logged in"** |
| Pages source = GitHub Actions | ⛔ **Blocked — requires authenticated `gh`/admin UI** |
| Live URL reachable | ⛔ **Blocked — no deployment has ever run; `main` has not received Sprint 3 or 4** |
| Install-over-previous alpha | ⛔ **Blocked — requires a successful deployment** |

The blocker is environmental, not a code defect: the four tickets that depend on it are marked
"externally blocked" in `sprint_4_qa_s4-01.md`. Once the steps above are executed, no further code
change is required.

## 7. Troubleshooting

| Symptom | Cause | Fix |
| :--- | :--- | :--- |
| `Missing repository secret(s): ALPHA_KEY_ALIAS` | Secret not set, or set on an environment instead of the repository | Re-run step 2; repository secrets are required |
| `The keystore does not open with ...` | Wrong store password or alias | Re-check the values used at key generation; do not regenerate the key unless it is lost |
| `HttpError: Not Found` from `gh api .../pages` | Pages is not enabled yet | Enable Pages → Source: GitHub Actions in repository settings, then re-run |
| Deploy job fails with `Get Pages site failed` | Pages source is still a branch | Run the `PUT`/`POST` command in step 3 |
| Portal loads but the APK 404s | The workflow ran on a tag that did not stage `public/` | Re-run the workflow with `workflow_dispatch` and check the `Stage APK` step |
| Testers get `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | A different certificate signed the previous build | Uninstall once; then verify consecutive builds share the SHA-256 from `alpha-signing.md` |

## 8. Rollback

The portal is a static artifact. To roll back, re-run the workflow from the last known-good commit
(`gh workflow run deploy-pages.yml --ref <sha>`) or disable Pages temporarily. Secrets do not need to
change unless the key itself is compromised.
