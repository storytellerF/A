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
const { PNG } = await import(
  pathToFileURL(resolve(work, "browser/node_modules/pngjs/lib/png.js"))
);
const site = await startSite();
const results = [],
  comparisons = [];
function quantile(xs, q) {
  const s = xs.toSorted((a, b) => a - b);
  return s.length ? s[Math.min(s.length - 1, Math.floor(s.length * q))] : 0;
}
try {
  for (const [browserName, executablePath] of Object.entries(
    chromiumBinaries,
  )) {
    const browser = await chromium.launch({ executablePath, headless: true });
    try {
      for (const [name, source, w, h] of [
        ["av", "fixture.mp4", 320, 180],
        ["hd", "fixture-hd.mp4", 1280, 720],
        ["fhd", "fixture-fhd.mp4", 1920, 1080],
      ]) {
        let reference;
        for (let round = 0; round < 3; round++) {
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
              const cdp = await page.context().newCDPSession(page);
              await cdp.send("Performance.enable");
              await page.goto(
                `${site.origin}/compose/?media=/${source}&mediaPath=${path}`,
              );
              await page.waitForFunction(() => mediaTest.decoded.length > 0);
              await page
                .locator("canvas")
                .evaluate((c) => (mediaTest.canvas = c));
              await page.evaluate(
                () =>
                  new Promise((r) =>
                    requestAnimationFrame(() => requestAnimationFrame(r)),
                  ),
              );
              if (round === 0) {
                const shot = await page.screenshot();
                await writeFile(
                  resolve(
                    work,
                    `comparison-${browserName}-${name}-${path}.png`,
                  ),
                  shot,
                );
                const png = PNG.sync.read(shot);
                if (path === "rgba-copy") reference = png;
                else {
                  const delta = [];
                  for (let y = 95; y < 425; y++)
                    for (let x = 25; x < 615; x++)
                      for (let c = 0; c < 3; c++)
                        delta.push(
                          Math.abs(
                            png.data[(y * png.width + x) * 4 + c] -
                              reference.data[(y * reference.width + x) * 4 + c],
                          ),
                        );
                  const mae = delta.reduce((a, b) => a + b, 0) / delta.length;
                  assert.ok(mae < 5, `Colour/orientation mismatch: ${mae}`);
                  comparisons.push({
                    browser: browserName,
                    source,
                    meanAbsoluteChannelError: mae,
                    p95ChannelError: quantile(delta, 0.95),
                  });
                }
              }
              await page.evaluate(() => {
                const stream = mediaTest.canvas.captureStream();
                const reader = new MediaStreamTrackProcessor({
                  track: stream.getVideoTracks()[0],
                }).readable.getReader();
                mediaTest.capture = {
                  stream,
                  reader,
                  running: true,
                  frames: [],
                  error: "",
                };
                mediaTest.capture.task = (async () => {
                  try {
                    while (mediaTest.capture.running) {
                      const { value: f, done } = await reader.read();
                      if (done) break;
                      try {
                        const o = {
                          format: "RGBA",
                          rect: { x: 16, y: 88, width: 608, height: 342 },
                        };
                        const a = new Uint8Array(f.allocationSize(o));
                        await f.copyTo(a, o);
                        let hash = 0;
                        for (let i = 0; i < a.length; i += 97)
                          hash = (hash * 31 + a[i]) | 0;
                        mediaTest.capture.frames.push({
                          time: performance.now(),
                          hash,
                        });
                      } finally {
                        f.close();
                      }
                    }
                  } catch (e) {
                    mediaTest.capture.error = e.message;
                  }
                })();
              });
              await page.mouse.click(50, 452);
              await page.waitForFunction(
                () =>
                  mediaTest.decoded.length >= 10 &&
                  mediaTest.contexts.some((c) => c.state === "running"),
              );
              const start = await page.evaluate(() => performance.now());
              const before = Object.fromEntries(
                (await cdp.send("Performance.getMetrics")).metrics.map((x) => [
                  x.name,
                  x.value,
                ]),
              );
              await page.waitForTimeout(2000);
              const after = Object.fromEntries(
                (await cdp.send("Performance.getMetrics")).metrics.map((x) => [
                  x.name,
                  x.value,
                ]),
              );
              const sample = await page.evaluate(
                ({ start, w, h }) => {
                  const end = performance.now(),
                    frames = mediaTest.decoded.filter(
                      (f) => f.time >= start && f.time < end,
                    ),
                    uploads = mediaTest.uploads.filter(
                      (u) =>
                        u.w === w &&
                        u.h === h &&
                        u.time >= start &&
                        u.time < end,
                    );
                  return {
                    elapsed: (end - start) / 1000,
                    decoded: frames.length,
                    capturedUnique: new Set(
                      mediaTest.capture.frames
                        .filter((f) => f.time >= start && f.time < end)
                        .map((f) => f.hash),
                    ).size,
                    copyBytes: frames.reduce((s, f) => s + f.bytes, 0),
                    readbackMs: frames.reduce((s, f) => s + f.readbackMs, 0),
                    uploadMs: uploads.map((u) => u.ms),
                    frameUploads: uploads.filter((u) => u.frame).length,
                    captureError: mediaTest.capture.error,
                    peakFrames: mediaTest.peakFrames,
                    peakTextures: mediaTest.peakTextures,
                    gl: mediaTest.glInfo,
                  };
                },
                { start, w, h },
              );
              assert.equal(sample.captureError, "");
              assert.ok(sample.capturedUnique > 35, JSON.stringify(sample));
              assert.deepEqual(errors, []);
              if (path === "gpu-texture") {
                assert.equal(sample.copyBytes, 0);
                assert.ok(sample.frameUploads > 35);
                assert.equal(
                  await page.evaluate(
                    () =>
                      mediaTest.frameCanvases.size === 1 &&
                      mediaTest.frameCanvases.has(mediaTest.canvas),
                  ),
                  true,
                );
              }
              await page.evaluate(async () => {
                mediaTest.capture.running = false;
                await mediaTest.capture.reader.cancel();
                mediaTest.capture.stream.getTracks().forEach((t) => t.stop());
                await mediaTest.capture.task;
              });
              await page.mouse.click(90, 64);
              await page.waitForFunction(
                () =>
                  mediaTest.workers.every((w) => w.closed) &&
                  mediaTest.transferredFrames.size === 0 &&
                  mediaTest.frameTextures.size === 0 &&
                  mediaTest.contexts.every((c) => c.state === "closed"),
              );
              const row = {
                browser: browserName,
                version: browser.version(),
                source,
                width: w,
                height: h,
                path,
                round,
                ...sample,
                capturePolicy: "per-paint",
                canvasCaptureUniqueFps: sample.capturedUnique / sample.elapsed,
                readbackMsPerDecoded: sample.readbackMs / sample.decoded,
                uploadMsP50: quantile(sample.uploadMs, 0.5),
                uploadMsP95: quantile(sample.uploadMs, 0.95),
                mainThreadTaskMs:
                  (after.TaskDuration - before.TaskDuration) * 1000,
                mainThreadScriptMs:
                  (after.ScriptDuration - before.ScriptDuration) * 1000,
                jsHeapEndBytes: after.JSHeapUsedSize,
                disposed: true,
              };
              results.push(row);
              console.info(
                JSON.stringify({
                  browser: browserName,
                  source,
                  path,
                  round,
                  canvasCaptureFps: row.canvasCaptureUniqueFps,
                  taskMs: row.mainThreadTaskMs,
                }),
              );
            } finally {
              await page.close();
            }
          }
        }
      }
    } finally {
      await browser.close();
    }
  }
  await writeFile(
    resolve(work, "gpu-comparison-results.json"),
    JSON.stringify(
      {
        measurementSchema: 3,
        build: composeBuild,
        viewport: { width: 900, height: 800 },
        videoDisplayArea: { width: 608, height: 342 },
        method:
          "Same compiled Compose scene and native runtime; 2 second samples after 10 decoded frames; three interleaved rounds per source/path/browser; canvas capture overhead included; main-thread task CPU proxy from CDP; upload durations are CPU call time, not GPU completion time",
        comparisons,
        results,
      },
      null,
      2,
    ),
  );
} finally {
  await site.close();
}
