#!/usr/bin/env node
/**
 * Renders the GitHub Pages alpha portal (SMO-S3-05).
 *
 * Inputs, all optional so the script also runs locally:
 *   APK_PATH    path to the built debug APK (used for the size badge)
 *   PAGES_URL   absolute base URL of the site, used as the QR payload
 *   VERSION     release label; defaults to `alpha-<short sha>` when COMMIT is present
 *   COMMIT      commit SHA shown in the metadata chips
 *   BUILD_TIME  human-readable build timestamp; defaults to the current UTC time
 *
 * Outputs: `public/index.html` plus `public/qr.svg` (pre-rendered, so the page needs no
 * JavaScript and no third-party QR service at view time). The APK itself is copied by the
 * workflow, not here.
 */
"use strict";

const fs = require("fs");
const path = require("path");
const qrcode = require("../pages/qrcode.js");

const ROOT = path.resolve(__dirname, "..");
const OUTPUT_DIR = path.join(ROOT, "public");
const APK_NAME = "showmeoutfit-alpha.apk";
const DEFAULT_PAGES_URL = "https://smoh-apps.github.io/ShowMeOutfit/";

const baseUrl = (process.env.PAGES_URL || DEFAULT_PAGES_URL).replace(/\/+$/, "") + "/";
const commit = (process.env.COMMIT || "").trim();
const version = (process.env.VERSION || "").trim() ||
  (commit ? `alpha-${commit.slice(0, 7)}` : "alpha-local");
const buildTime = (process.env.BUILD_TIME || "").trim() || utcNow();
const apkPath = process.env.APK_PATH || path.join(ROOT, "app/build/outputs/apk/debug/app-debug.apk");

const apkSize = fs.existsSync(apkPath) ? formatMegabytes(fs.statSync(apkPath).size) : "size at download";

fs.mkdirSync(OUTPUT_DIR, { recursive: true });

const qr = qrcode(0, "M");
qr.addData(baseUrl + APK_NAME);
qr.make();
fs.writeFileSync(path.join(OUTPUT_DIR, "qr.svg"), qr.createSvgTag({ cellSize: 6, margin: 2, scalable: true }) + "\n");

const html = fs
  .readFileSync(path.join(ROOT, "pages/index.html"), "utf8")
  .replaceAll("__VERSION__", escapeHtml(version))
  .replaceAll("__BUILD_TIME__", escapeHtml(buildTime))
  .replaceAll("__COMMIT__", escapeHtml(commit ? commit.slice(0, 7) : "local build"))
  .replaceAll("__APK_SIZE__", escapeHtml(apkSize));

fs.writeFileSync(path.join(OUTPUT_DIR, "index.html"), html);

console.log(`portal: ${path.join(OUTPUT_DIR, "index.html")}`);
console.log(`qr payload: ${baseUrl}${APK_NAME}`);

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
