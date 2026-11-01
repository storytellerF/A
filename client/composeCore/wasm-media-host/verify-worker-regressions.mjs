/* This is a private project. All rights reserved. */
import { runCase, launchBrowser, ownSite } from "./verification-runner.mjs";
import { getReportDirectory } from "./verification-reports.mjs";
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { writeFile } from "node:fs/promises";
import { startSite, work, chromiumBinaries } from "./verification-site.mjs";
import { installMediaDiagnostics } from "./verification-instrumentation.mjs";

const { chromium } = await import(
  pathToFileURL(resolve(work, "browser/node_modules/playwright/index.mjs"))
);

async function verifyNetworkFailure(browser, origin, phase) {
  const page = await browser.newPage();
  const errors = [];
  let requests = 0;
  page.on("pageerror", (error) => errors.push(error.message));
  const blockSource = () =>
    page.route("**/fixture.webm", (route) => {
      requests++;
      return route.abort();
    });
  try {
    if (phase === "open") await blockSource();
    await page.goto(origin);
    if (phase === "pause") {
      await page.waitForFunction(() => host.status === "paused");
      await page.click("#play");
      await page.waitForFunction(() => host.status === "playing");
      await blockSource();
      await page.evaluate(() => host.pause());
    }
    await page.waitForFunction(() => host.status === "error", null, {
      timeout: 5000,
    });
    const state = await page.evaluate(async () => {
      await host.audioClosing;
      return {
        error: host.errorMessage,
        worker: host.worker,
        pending: host.pending.size,
        nodes: host.nodes.size,
        context: host.context,
      };
    });
    assert.ok(state.error.includes("Media playback failed"));
    assert.equal(state.worker, null);
    assert.equal(state.pending, 0);
    assert.equal(state.nodes, 0);
    assert.equal(state.context, null);
    assert.ok(requests > 0);
    const stopped = requests;
    // The default first retry occurs within one second. No retry may survive failure.
    await page.waitForTimeout(1200);
    assert.equal(requests, stopped);
    assert.deepEqual(errors, []);
    return { phase, status: "error", requests };
  } finally {
    await page.evaluate(() => host.close()).catch(() => {});
    await page.close();
  }
}

async function verifyTrailingSkips(browser, origin, path) {
  const page = await browser.newPage();
  const errors = [];
  page.on("pageerror", (error) => errors.push(error.message));
  try {
    await installMediaDiagnostics(page);
    await page.goto(`${origin}/?src=/silent.webm&mediaPath=${path}`);
    await page.waitForFunction(() => host.status === "paused");
    const result = await page.evaluate(async () => {
      const before = { ...host.metrics };
      const frames = mediaTest.decoded.length;
      const eof = await host.rpc("video", { time: host.duration + 1 });
      return {
        path: host.path,
        eof,
        before,
        after: { ...host.metrics },
        frames,
        afterFrames: mediaTest.decoded.length,
        diagnosticSkips: mediaTest.videoSkips.reduce(
          (sum, s) => sum + s.count,
          0,
        ),
      };
    });
    const skipped =
      result.after.skippedBeforeConversion -
      result.before.skippedBeforeConversion;
    assert.equal(result.path, path);
    if (path === "gpu-texture") assert.equal(result.before.copiedBytes, 0);
    else assert.ok(result.before.copiedBytes > 0);
    assert.equal(result.eof, null);
    assert.ok(skipped > 0);
    assert.equal(result.after.decoded, result.before.decoded);
    assert.equal(result.after.acceptedFrames, result.before.acceptedFrames);
    assert.equal(result.after.copiedBytes, result.before.copiedBytes);
    assert.equal(result.after.readbackMs, result.before.readbackMs);
    assert.equal(result.afterFrames, result.frames);
    assert.equal(result.diagnosticSkips, result.after.skippedBeforeConversion);
    assert.deepEqual(errors, []);
    return { path, trailingSkippedSamples: skipped };
  } finally {
    await page.evaluate(() => host.close()).catch(() => {});
    await page.close();
  }
}

await runCase("verify-worker-regressions", async () => {
  const site = ownSite(await startSite());
  const results = [];
  try {
    for (const [name, executablePath] of Object.entries(chromiumBinaries)) {
      await runCase(`browser ${name}`, async () => {
        const browser = await launchBrowser(chromium, {
          executablePath,
          headless: true,
        });
        try {
          for (const phase of ["open", "pause"]) {
            await runCase(`verifyNetworkFailure ${phase}`, async () => {
              results.push({
                browser: name,
                ...(await verifyNetworkFailure(browser, site.origin, phase)),
              });
            });
          }
          for (const path of ["rgba-copy", "gpu-texture"]) {
            await runCase(`verifyTrailingSkips ${path}`, async () => {
              results.push({
                browser: name,
                ...(await verifyTrailingSkips(browser, site.origin, path)),
              });
            });
          }
        } finally {
          await browser.close();
        }
      });
    }
    await writeFile(
      resolve(await getReportDirectory(), "worker-regressions.json"),
      JSON.stringify(results, null, 2),
    );
    console.info(JSON.stringify(results, null, 2));
  } finally {
    await site.close();
  }
});
