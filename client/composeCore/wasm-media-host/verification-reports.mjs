/* This is a private project. All rights reserved. */
import { cp, mkdir, mkdtemp, rename, rmdir, writeFile } from "node:fs/promises";
import { basename, resolve } from "node:path";
import { root } from "./verification-site.mjs";

export const rawRoot = resolve(root, "build/test-results/wasmMediaBrowserTest");
export const collectedRoot = resolve(root, "build/reports/wasm-media");
let directory;

// Each verification process writes only to its own suite directory.
export function getReportDirectory() {
  directory ??= (async () => {
    const parent = process.env.MEDIA_REPORT_DIR ?? rawRoot;
    await mkdir(parent, { recursive: true });
    const suite =
      process.env.MEDIA_VERIFY_SUITE ??
      basename(process.argv[1] ?? "standalone", ".mjs");
    return mkdtemp(resolve(parent, `${suite}-`));
  })();
  return directory;
}

export async function createReportRun(raw = rawRoot) {
  await mkdir(raw, { recursive: true });
  return mkdtemp(resolve(raw, "run-"));
}

// Publish a complete, immutable copy after every batch has terminated. A failed
// collection leaves raw evidence and all previously collected runs untouched.
export async function collectReportRun(run, destination = collectedRoot) {
  await mkdir(destination, { recursive: true });
  const staging = await mkdtemp(resolve(destination, ".collect-"));
  const snapshot = resolve(staging, "snapshot");
  await cp(run, snapshot, {
    recursive: true,
    errorOnExist: true,
    force: false,
  });
  const published = resolve(destination, basename(run));
  await rename(snapshot, published);
  await rmdir(staging);
  return published;
}

export async function writeRunManifest(run, batches) {
  await writeFile(
    resolve(run, "manifest.json"),
    JSON.stringify({ batches }, null, 2),
  );
}
