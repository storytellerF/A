/* This is a private project. All rights reserved. */
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { writeFile } from "node:fs/promises";
import {
  startSite,
  work,
  chromiumBinaries,
  composeBuild,
} from "./verification-site.mjs";
import { installMediaDiagnostics } from "./verification-instrumentation.mjs";
const { chromium } = await import(
  pathToFileURL(resolve(work, "browser/node_modules/playwright/index.mjs"))
);
const site = await startSite(),
  results = [];
try {
  for (const [browserName, executablePath] of Object.entries(
    chromiumBinaries,
  )) {
    const browser = await chromium.launch({ executablePath, headless: true });
    try {
      for (const [source, w, h] of [
        ["fixture-hd.mp4", 1280, 720],
        ["fixture-fhd.mp4", 1920, 1080],
      ])
        for (let round = 0; round < 3; round++)
          for (const capture of [false, true])
            for (const path of round % 2
              ? ["gpu-texture", "rgba-copy"]
              : ["rgba-copy", "gpu-texture"]) {
              const page = await browser.newPage({
                viewport: { width: 900, height: 800 },
                locale: "en-US",
              });
              const errors = [];
              page.on("pageerror", (e) => errors.push(e.message));
              try {
                await installMediaDiagnostics(page);
                await page.addInitScript(() => {
                  window.presentationProfile = { calls: {}, raf: [] };
                  const raf = requestAnimationFrame;
                  window.requestAnimationFrame = (cb) =>
                    raf.call(window, (t) => {
                      const start = performance.now();
                      try {
                        return cb(t);
                      } finally {
                        presentationProfile.raf.push({
                          time: t,
                          ms: performance.now() - start,
                        });
                      }
                    });
                  for (const method of [
                    "getParameter",
                    "getError",
                    "activeTexture",
                    "bindTexture",
                    "bindBuffer",
                    "pixelStorei",
                    "texParameteri",
                    "flush",
                    "finish",
                    "bindFramebuffer",
                    "useProgram",
                    "enable",
                    "disable",
                    "blendFuncSeparate",
                    "stencilFuncSeparate",
                    "stencilOpSeparate",
                    "viewport",
                    "scissor",
                  ]) {
                    const original = WebGL2RenderingContext.prototype[method];
                    WebGL2RenderingContext.prototype[method] = function (
                      ...args
                    ) {
                      const start = performance.now();
                      try {
                        return original.apply(this, args);
                      } finally {
                        const row = (presentationProfile.calls[method] ??= {
                          count: 0,
                          ms: 0,
                        });
                        row.count++;
                        row.ms += performance.now() - start;
                      }
                    };
                  }
                });
                const cdp = await page.context().newCDPSession(page);
                await cdp.send("Performance.enable");
                await page.goto(
                  `${site.origin}/compose/?media=/${source}&mediaPath=${path}&gpuImport=${process.env.MEDIA_GPU_IMPORT ?? "legacy"}`,
                );
                await page.waitForFunction(() => mediaTest.decoded.length > 0);
                await page
                  .locator("canvas")
                  .evaluate((c) => (mediaTest.canvas = c));
                if (capture)
                  await page.evaluate(() => {
                    const stream = mediaTest.canvas.captureStream(),
                      reader = new MediaStreamTrackProcessor({
                        track: stream.getVideoTracks()[0],
                      }).readable.getReader();
                    mediaTest.capture = {
                      stream,
                      reader,
                      running: true,
                      hashes: [],
                    };
                    mediaTest.capture.task = (async () => {
                      while (mediaTest.capture.running) {
                        const { value: f, done } = await reader.read();
                        if (done) break;
                        try {
                          const o = {
                              format: "RGBA",
                              rect: { x: 16, y: 88, width: 608, height: 342 },
                            },
                            a = new Uint8Array(f.allocationSize(o));
                          await f.copyTo(a, o);
                          let hash = 0;
                          for (let i = 0; i < a.length; i += 97)
                            hash = (hash * 31 + a[i]) | 0;
                          mediaTest.capture.hashes.push(hash);
                        } finally {
                          f.close();
                        }
                      }
                    })();
                  });
                await page.mouse.click(50, 452);
                await page.waitForFunction(
                  () => mediaTest.decoded.length >= 10,
                );
                const start = await page.evaluate(() => {
                  presentationProfile.calls = {};
                  presentationProfile.raf = [];
                  if (mediaTest.capture) mediaTest.capture.hashes = [];
                  return performance.now();
                });
                const before = Object.fromEntries(
                  (await cdp.send("Performance.getMetrics")).metrics.map(
                    (x) => [x.name, x.value],
                  ),
                );
                await page.waitForTimeout(2000);
                const after = Object.fromEntries(
                  (await cdp.send("Performance.getMetrics")).metrics.map(
                    (x) => [x.name, x.value],
                  ),
                );
                const sample = await page.evaluate(
                  ({ start, w, h }) => ({
                    elapsed: (performance.now() - start) / 1000,
                    decoded: mediaTest.decoded.filter((f) => f.time >= start)
                      .length,
                    imports: mediaTest.uploads.filter(
                      (u) => u.w === w && u.h === h && u.time >= start,
                    ).length,
                    capturedUnique: mediaTest.capture
                      ? new Set(mediaTest.capture.hashes).size
                      : null,
                    calls: presentationProfile.calls,
                    raf: presentationProfile.raf.map((r) => r.ms),
                  }),
                  { start, w, h },
                );
                assert.deepEqual(errors, []);
                assert.ok(sample.imports > 35);
                if (capture)
                  await page.evaluate(async () => {
                    mediaTest.capture.running = false;
                    await mediaTest.capture.reader.cancel();
                    mediaTest.capture.stream
                      .getTracks()
                      .forEach((t) => t.stop());
                    await mediaTest.capture.task;
                  });
                await page.mouse.click(90, 64);
                await page.waitForFunction(
                  () =>
                    mediaTest.workers.every((w) => w.closed) &&
                    mediaTest.transferredFrames.size === 0 &&
                    mediaTest.frameTextures.size === 0,
                );
                const row = {
                  browser: browserName,
                  build: composeBuild,
                  strategy: process.env.MEDIA_GPU_IMPORT ?? "legacy",
                  source,
                  path,
                  capture,
                  capturePolicy: capture ? "per-paint" : "disabled",
                  round,
                  ...sample,
                  mainTaskMs: (after.TaskDuration - before.TaskDuration) * 1000,
                };
                results.push(row);
                console.info(
                  JSON.stringify({
                    browser: browserName,
                    source,
                    path,
                    capture,
                    round,
                    imports: row.imports,
                    captured: row.capturedUnique,
                    cpu: row.mainTaskMs,
                  }),
                );
              } finally {
                await page.close();
              }
            }
    } finally {
      await browser.close();
    }
  }
  await writeFile(
    resolve(
      work,
      process.env.MEDIA_PROFILE_OUTPUT ?? "gpu-presentation-profile.json",
    ),
    JSON.stringify(results, null, 2),
  );
} finally {
  await site.close();
}
