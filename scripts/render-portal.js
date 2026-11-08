#!/usr/bin/env node
/**
 * Renders the GitHub Pages distribution portal (SMO-S3-05, SMO-S4-08).
 *
 * Inputs, all optional so the script also runs locally:
 *   APK_PATH         path to the built debug APK (used for the alpha size badge)
 *   RELEASE_APK_PATH path to the built release APK (used for the release size badge)
 *   PAGES_URL        absolute base URL of the site, used as the QR payload
 *   VERSION          alpha release label; defaults to `alpha-<short sha>` when COMMIT is present
 *   RELEASE_VERSION  production release label; defaults to `v1.0.0`
 *   COMMIT           commit SHA shown in the metadata chips
 *   BUILD_TIME       human-readable build timestamp; defaults to the current UTC time
 *
 * Outputs: `public/index.html`, `public/privacy.html`, `public/qr-release.svg`,
 * `public/qr-alpha.svg`, and `public/qr.svg` (pointing to the primary release APK).
 */
"use strict";

const fs = require("fs");
const path = require("path");
const qrcode = require("../pages/qrcode.js");

const ROOT = path.resolve(__dirname, "..");
const OUTPUT_DIR = path.join(ROOT, "public");
const ALPHA_APK_NAME = "showmeoutfit-alpha.apk";
const RELEASE_APK_NAME = "showmeoutfit-release.apk";
const DEFAULT_PAGES_URL = "https://smoh-apps.github.io/ShowMeOutfit/";

const baseUrl = (process.env.PAGES_URL || DEFAULT_PAGES_URL).replace(/\/+$/, "") + "/";
const commit = (process.env.COMMIT || "").trim();
const version = (process.env.VERSION || "").trim() ||
  (commit ? `alpha-${commit.slice(0, 7)}` : "alpha-local");
const releaseVersion = (process.env.RELEASE_VERSION || "v1.0.0").trim();
const buildTime = (process.env.BUILD_TIME || "").trim() || utcNow();

const alphaApkPath = process.env.APK_PATH || path.join(ROOT, "app/build/outputs/apk/debug/app-debug.apk");
const releaseApkPath = process.env.RELEASE_APK_PATH || path.join(ROOT, "app/build/outputs/apk/release/app-release.apk");

fs.mkdirSync(OUTPUT_DIR, { recursive: true });

// Stage privacy policy
const privacySrc = path.join(ROOT, "pages/privacy.html");
if (fs.existsSync(privacySrc)) {
  fs.copyFileSync(privacySrc, path.join(OUTPUT_DIR, "privacy.html"));
}

// Stage release APK if present in build outputs and not yet in public
const targetReleaseApk = path.join(OUTPUT_DIR, RELEASE_APK_NAME);
if (fs.existsSync(releaseApkPath) && !fs.existsSync(targetReleaseApk)) {
  fs.copyFileSync(releaseApkPath, targetReleaseApk);
}

// Stage alpha APK if present in build outputs and not yet in public
const targetAlphaApk = path.join(OUTPUT_DIR, ALPHA_APK_NAME);
if (fs.existsSync(alphaApkPath) && !fs.existsSync(targetAlphaApk)) {
  fs.copyFileSync(alphaApkPath, targetAlphaApk);
}

const hasRelease = fs.existsSync(targetReleaseApk) || fs.existsSync(releaseApkPath);
const releaseApkSize = hasRelease
  ? formatMegabytes(fs.statSync(fs.existsSync(targetReleaseApk) ? targetReleaseApk : releaseApkPath).size)
  : "4.8 MB";

const hasAlpha = fs.existsSync(targetAlphaApk) || fs.existsSync(alphaApkPath);
const alphaApkSize = hasAlpha
  ? formatMegabytes(fs.statSync(fs.existsSync(targetAlphaApk) ? targetAlphaApk : alphaApkPath).size)
  : "size at download";

// Generate QR codes for both channels
const qrRelease = qrcode(0, "M");
qrRelease.addData(baseUrl + RELEASE_APK_NAME);
qrRelease.make();
const qrReleaseSvg = qrRelease.createSvgTag({ cellSize: 6, margin: 2, scalable: true }) + "\n";
fs.writeFileSync(path.join(OUTPUT_DIR, "qr-release.svg"), qrReleaseSvg);
fs.writeFileSync(path.join(OUTPUT_DIR, "qr.svg"), qrReleaseSvg); // Default qr.svg links to release

const qrAlpha = qrcode(0, "M");
qrAlpha.addData(baseUrl + ALPHA_APK_NAME);
qrAlpha.make();
const qrAlphaSvg = qrAlpha.createSvgTag({ cellSize: 6, margin: 2, scalable: true }) + "\n";
fs.writeFileSync(path.join(OUTPUT_DIR, "qr-alpha.svg"), qrAlphaSvg);

const html = fs
  .readFileSync(path.join(ROOT, "pages/index.html"), "utf8")
  .replaceAll("__RELEASE_VERSION__", escapeHtml(releaseVersion))
  .replaceAll("__RELEASE_APK_SIZE__", escapeHtml(releaseApkSize))
  .replaceAll("__VERSION__", escapeHtml(version))
  .replaceAll("__BUILD_TIME__", escapeHtml(buildTime))
  .replaceAll("__COMMIT__", escapeHtml(commit ? commit.slice(0, 7) : "local build"))
  .replaceAll("__APK_SIZE__", escapeHtml(alphaApkSize));

fs.writeFileSync(path.join(OUTPUT_DIR, "index.html"), html);

console.log(`portal: ${path.join(OUTPUT_DIR, "index.html")}`);
console.log(`release apk: ${baseUrl}${RELEASE_APK_NAME} (${releaseApkSize})`);
console.log(`alpha apk: ${baseUrl}${ALPHA_APK_NAME} (${alphaApkSize})`);

function formatMegabytes(bytes) {
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function utcNow() {
  return new Date().toISOString().replace("T", " ").slice(0, 16) + " UTC";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
