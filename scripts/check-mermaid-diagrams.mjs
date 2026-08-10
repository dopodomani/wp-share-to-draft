#!/usr/bin/env node
// Extracts every ```mermaid fenced block from the repo's Markdown files and renders
// each one with @mermaid-js/mermaid-cli to catch syntax errors before they ship --
// see .github/workflows/docs-ci.yml. Run locally with:
//   node scripts/check-mermaid-diagrams.mjs
import { execFileSync } from "node:child_process";
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { globSync } from "node:fs";

const IGNORE_DIRS = ["node_modules", "vendor", "build", "dist", ".git"];

function findMarkdownFiles(dir) {
  const results = [];
  for (const entry of globSync("**/*.md", { cwd: dir })) {
    const segments = entry.split(/[\\/]/);
    if (IGNORE_DIRS.some((ignored) => segments.includes(ignored))) continue;
    results.push(join(dir, entry));
  }
  return results;
}

function extractMermaidBlocks(markdown) {
  const blocks = [];
  const fenceRe = /```mermaid\r?\n([\s\S]*?)```/g;
  let match;
  while ((match = fenceRe.exec(markdown)) !== null) {
    blocks.push(match[1]);
  }
  return blocks;
}

const repoRoot = new URL("..", import.meta.url).pathname.replace(/^\/([a-zA-Z]:)/, "$1");
const files = findMarkdownFiles(repoRoot);

let totalBlocks = 0;
let failures = 0;
const tmpDir = mkdtempSync(join(tmpdir(), "mermaid-check-"));

// GitHub Actions' Ubuntu runners have no usable Chromium sandbox namespace, so
// mermaid-cli's headless-Chrome render fails outright without this -- see
// https://pptr.dev/troubleshooting. Safe here: the only page ever loaded is
// mermaid-cli's own local renderer over this repo's own trusted diagram source.
const puppeteerConfigPath = join(tmpDir, "puppeteer-config.json");
writeFileSync(puppeteerConfigPath, JSON.stringify({ args: ["--no-sandbox", "--disable-setuid-sandbox"] }));

try {
  for (const file of files) {
    const content = readFileSync(file, "utf-8");
    const blocks = extractMermaidBlocks(content);
    blocks.forEach((block, i) => {
      totalBlocks += 1;
      const inputPath = join(tmpDir, `block.mmd`);
      const outputPath = join(tmpDir, `block.svg`);
      writeFileSync(inputPath, block, "utf-8");
      try {
        execFileSync(
          process.platform === "win32" ? "npx.cmd" : "npx",
          [
            "--yes",
            "@mermaid-js/mermaid-cli",
            "-i",
            inputPath,
            "-o",
            outputPath,
            "--quiet",
            "--puppeteerConfigFile",
            puppeteerConfigPath,
          ],
          { stdio: "pipe", shell: process.platform === "win32" },
        );
      } catch (err) {
        failures += 1;
        console.error(`\n✗ ${file} (diagram #${i + 1})`);
        console.error(err.stdout?.toString() ?? err.message);
        console.error(err.stderr?.toString() ?? "");
      }
    });
  }
} finally {
  rmSync(tmpDir, { recursive: true, force: true });
}

console.log(`\nChecked ${totalBlocks} mermaid diagram(s) across ${files.length} markdown file(s).`);
if (failures > 0) {
  console.error(`${failures} diagram(s) failed to render.`);
  process.exit(1);
}
