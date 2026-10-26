/* This is a private project. All rights reserved. */
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { writeFile, readFile } from "node:fs/promises";
import { randomUUID } from "node:crypto";
import {
  startSite,
  work,
  chromiumBinaries,
  composeBuild,
} from "./verification-site.mjs";
import { installMediaDiagnostics } from "./verification-instrumentation.mjs";
import { installGpuDrawDiagnostics } from "./gpu-draw-diagnostics.mjs";
import {
  createDesktopProbe,
  startGraphicsTrace,
} from "./headed-diagnostics.mjs";
const { chromium } = await import(
  pathToFileURL(resolve(work, "browser/node_modules/playwright/index.mjs"))
);
const { PNG } = await import(
  pathToFileURL(resolve(work, "browser/node_modules/pngjs/lib/png.js"))
);
assert.equal(composeBuild, "production", "Use MEDIA_COMPOSE_BUILD=production");
const headed = process.env.MEDIA_BENCH_HEADED === "1";
const rounds = Number(process.env.MEDIA_BENCH_ROUNDS ?? 3),
  seconds = Number(process.env.MEDIA_BENCH_SECONDS ?? 5),
  profiling = process.env.MEDIA_BENCH_PROFILE === "1";
const cases = (process.env.MEDIA_BENCH_CASES ?? "720-30,1080-30,720-60,1080-60")
  .split(",")
  .map((c) => c.split("-").map(Number));
const variants = [
  { name: "rgba-copy", path: "rgba-copy" },
  { name: "gpu-legacy", path: "gpu-texture", strategy: "legacy" },
  { name: "gpu-restored", path: "gpu-texture", strategy: "restored" },
  ...(process.env.MEDIA_BENCH_QUEUE_STUDY === "1"
    ? [
        {
          name: "gpu-restored-buffer8",
          path: "gpu-texture",
          strategy: "restored",
          captureBuffer: 8,
        },
      ]
    : []),
  ...(headed && process.env.MEDIA_BENCH_OBSERVER_CONTROLS === "1"
    ? [
        {
          name: "gpu-restored-no-desktop",
          path: "gpu-texture",
          strategy: "restored",
          desktop: false,
          control: true,
        },
        {
          name: "gpu-restored-no-trace",
          path: "gpu-texture",
          strategy: "restored",
          trace: false,
          control: true,
        },
      ]
    : []),
].filter(
  (v) =>
    !process.env.MEDIA_BENCH_VARIANTS ||
    process.env.MEDIA_BENCH_VARIANTS.split(",").includes(v.name),
);
const site = await startSite(),
  results = [];
function q(xs, p) {
  const a = xs.toSorted((a, b) => a - b);
  return a.length ? a[Math.min(a.length - 1, Math.floor(a.length * p))] : 0;
}
try {
  for (const [browserName, executablePath] of Object.entries(
    chromiumBinaries,
  ).filter(
    ([n]) =>
      !process.env.MEDIA_BENCH_BROWSER || n === process.env.MEDIA_BENCH_BROWSER,
  )) {
    const browser = await chromium.launch({
      executablePath,
      headless: !headed,
      args: headed
        ? ["--force-device-scale-factor=1", "--window-position=0,0"]
        : [],
    });
    let sharedPage,
      sharedInitialized = false;
    if (headed)
      sharedPage = await browser.newPage({
        viewport: { width: 1952, height: 1360 },
        deviceScaleFactor: 1,
        locale: "en-US",
      });
    try {
      for (const [height, fps] of cases)
        for (let round = 0; round < rounds; round++) {
          const ordered = [
            ...variants.slice(round % variants.length),
            ...variants.slice(0, round % variants.length),
          ];
          for (const variant of ordered.filter(
            (v) => !v.control || round === 0,
          ))
            for (const capture of process.env.MEDIA_BENCH_CAPTURE === "both"
              ? round % 2 === 0
                ? [false, true]
                : [true, false]
              : process.env.MEDIA_BENCH_NO_CAPTURE === "1"
                ? [false]
                : [true]) {
              const width = height === 720 ? 1280 : 1920,
                source = `counter-${height}-${fps}.mp4`;
              const page =
                  sharedPage ??
                  (await browser.newPage({
                    viewport: { width: 1952, height: 1360 },
                    deviceScaleFactor: 1,
                    locale: "en-US",
                  })),
                errors = [];
              let desktop, graphics;
              const onPageError = (e) => errors.push(e.message);
              page.on("pageerror", onPageError);
              try {
                if (!sharedInitialized) {
                  await installMediaDiagnostics(page, {
                    frameStages: process.env.MEDIA_BENCH_FRAME_STAGES === "1",
                  });
                  if (process.env.MEDIA_BENCH_GPU_TRACE === "1")
                    await installGpuDrawDiagnostics(page, {
                      traceMarks: headed,
                    });
                  if (headed) sharedInitialized = true;
                }
                const cdp = await page.context().newCDPSession(page);
                await cdp.send("Performance.enable");
                if (headed) {
                  const { windowId } = await cdp.send(
                    "Browser.getWindowForTarget",
                  );
                  await cdp.send("Browser.setWindowBounds", {
                    windowId,
                    bounds: {
                      windowState: "normal",
                      left: 0,
                      top: 0,
                      width: 2048,
                      height: 1400,
                    },
                  });
                  await page.bringToFront();
                }
                await page.goto(
                  `${site.origin}/compose/?display=1080p&media=/${source}&mediaPath=${variant.path}&gpuImport=${variant.strategy ?? "restored"}&mediaProfile=${profiling}`,
                );
                await page.waitForFunction(() => mediaTest.decoded.length > 0);
                await page
                  .locator("canvas")
                  .evaluate((c) => (mediaTest.canvas = c));
                assert.deepEqual(
                  await page.locator("canvas").evaluate((c) => ({
                    cssWidth: c.clientWidth,
                    cssHeight: c.clientHeight,
                    width: c.width,
                    height: c.height,
                    dpr: devicePixelRatio,
                  })),
                  {
                    cssWidth: 1952,
                    cssHeight: 1360,
                    width: 1952,
                    height: 1360,
                    dpr: 1,
                  },
                );
                await page.evaluate(
                  () =>
                    new Promise((r) =>
                      requestAnimationFrame(() => requestAnimationFrame(r)),
                    ),
                );
                const png = PNG.sync.read(await page.screenshot());
                const rgb = (x, y) => [
                  ...png.data.subarray(
                    (y * png.width + x) * 4,
                    (y * png.width + x) * 4 + 3,
                  ),
                ];
                const [l, r, t, b] = [
                  rgb(18, 628),
                  rgb(1933, 628),
                  rgb(976, 90),
                  rgb(976, 1166),
                ];
                assert.ok(
                  l[0] > 170 && l[1] < 100 && l[2] < 100,
                  `Left edge ${l}`,
                );
                assert.ok(
                  r[1] > 170 && r[0] < 100 && r[2] < 100,
                  `Right edge ${r}`,
                );
                assert.ok(
                  t[0] > 170 && t[1] > 170 && t[2] < 100,
                  `Top edge ${t}`,
                );
                assert.ok(
                  b[2] > 170 && b[0] < 100 && b[1] < 100,
                  `Bottom edge ${b}`,
                );
                if (round === 0)
                  await page.screenshot({
                    path: resolve(
                      work,
                      `${process.env.MEDIA_BENCH_OUTPUT?.replace(/\.json$/, "") ?? "fullhd"}-${browserName}-${height}-${fps}-${variant.name}.png`,
                    ),
                  });
                if (
                  headed &&
                  !browser.foregroundReady &&
                  process.env.MEDIA_BENCH_FOREGROUND_GATE === "1"
                ) {
                  const token = randomUUID(),
                    title = "Media presentation probe " + token;
                  await page.evaluate(
                    (title) => (document.title = title),
                    title,
                  );
                  await writeFile(
                    resolve(work, "headed-window.json"),
                    JSON.stringify({ token, title, browser: browserName }),
                  );
                  for (;;) {
                    let ack;
                    try {
                      ack = JSON.parse(
                        await readFile(
                          resolve(work, "headed-foreground.json"),
                          "utf8",
                        ),
                      );
                    } catch {}
                    if (ack?.token === token) break;
                    await new Promise((r) => setTimeout(r, 200));
                  }
                  browser.foregroundReady = true;
                }
                if (headed && process.env.MEDIA_BENCH_DESKTOP !== "0")
                  desktop = await createDesktopProbe(browser, width, {
                    sampling: variant.desktop !== false,
                  });
                if (capture)
                  await page.evaluate(
                    async ({ width, queueSize }) => {
                      const scale = 1920 / width,
                        rect = {
                          x: 16 + Math.round(32 * scale),
                          y: 88 + Math.round(40 * scale),
                          width: Math.round(128 * scale),
                          height: 2,
                        };
                      const stream = mediaTest.canvas.captureStream(),
                        processor = new MediaStreamTrackProcessor({
                          track: stream.getVideoTracks()[0],
                          maxBufferSize: queueSize,
                        }),
                        readable = processor.readable;
                      const worker = new Worker("/counter-observer.mjs", {
                          type: "module",
                        }),
                        pending = new Map();
                      let request = 0;
                      worker.onmessage = ({ data }) => {
                        const call = pending.get(data.id);
                        pending.delete(data.id);
                        if (data.error) call.reject(new Error(data.error));
                        else call.resolve(data.result);
                      };
                      const rpc = (op, values = {}, transfer = []) =>
                        new Promise((resolve, reject) => {
                          const id = ++request;
                          pending.set(id, { resolve, reject });
                          worker.postMessage({ id, op, ...values }, transfer);
                        });
                      mediaTest.counter = {
                        stream,
                        processor,
                        worker,
                        rpc,
                        rect,
                      };
                      await rpc("start", { readable, rect, scale }, [readable]);
                    },
                    { width, queueSize: variant.captureBuffer ?? 1 },
                  );
                await page.mouse.click(50, 1190);
                await page.waitForFunction(
                  (fps) =>
                    mediaTest.decoded.length >= fps + 3 &&
                    mediaTest.contexts.some((c) => c.state === "running"),
                  fps,
                );
                if (desktop) await desktop.start();
                if (
                  headed &&
                  variant.trace !== false &&
                  process.env.MEDIA_BENCH_TRACE !== "0"
                )
                  graphics = await startGraphicsTrace(browser);
                const start = await page.evaluate(async () => {
                  performance.clearMeasures();
                  performance.clearMarks();
                  if (mediaTest.counter) await mediaTest.counter.rpc("reset");
                  if (mediaTest.counter) {
                    const p = mediaTest.counter.processor;
                    mediaTest.counter.startStats = {
                      totalFrames: p.totalFrames,
                      discardedFrames: p.discardedFrames,
                    };
                  }
                  performance.mark("media.sample.start");
                  return performance.now();
                });
                const before = Object.fromEntries(
                  (await cdp.send("Performance.getMetrics")).metrics.map(
                    (x) => [x.name, x.value],
                  ),
                );
                await page.waitForTimeout(seconds * 1000);
                const after = Object.fromEntries(
                  (await cdp.send("Performance.getMetrics")).metrics.map(
                    (x) => [x.name, x.value],
                  ),
                );
                const sample = await page.evaluate(
                  async ({ start, width, height }) => {
                    performance.mark("media.sample.end");
                    const end = performance.now(),
                      observer = mediaTest.counter
                        ? await mediaTest.counter.rpc("snapshot")
                        : { frames: [], error: "", closedFrames: 0 },
                      decoded = mediaTest.decoded.filter(
                        (f) => f.time >= start && f.time < end,
                      ),
                      uploads = mediaTest.uploads.filter(
                        (u) =>
                          u.w === width &&
                          u.h === height &&
                          u.time >= start &&
                          u.time < end,
                      ),
                      frames = observer.frames
                        .map((f) => ({
                          id: f.id,
                          time: f.epoch - performance.timeOrigin,
                          copyMs: f.copyMs,
                          captureTimestamp: f.timestamp,
                        }))
                        .filter((f) => f.time >= start && f.time < end),
                      unique = frames.filter(
                        (f, i) => i === 0 || f.id !== frames[i - 1].id,
                      ),
                      stages = {};
                    const pixelStages = {};
                    for (const e of performance.getEntriesByType("mark"))
                      if (e.name.startsWith("media.rgba."))
                        (pixelStages[e.name] ??= []).push(e.detail?.timestamp);
                    for (const e of performance.getEntriesByType("measure"))
                      if (e.name.startsWith("media.gpu."))
                        (stages[e.name] ??= []).push(e.duration);
                    return {
                      startEpoch: performance.timeOrigin + start,
                      endEpoch: performance.timeOrigin + end,
                      processorStats:
                        mediaTest.counter &&
                        typeof mediaTest.counter.processor.totalFrames ===
                          "number"
                          ? {
                              totalFrames:
                                mediaTest.counter.processor.totalFrames -
                                mediaTest.counter.startStats.totalFrames,
                              discardedFrames:
                                mediaTest.counter.processor.discardedFrames -
                                mediaTest.counter.startStats.discardedFrames,
                            }
                          : null,
                      captureCopyMs: frames.map((f) => f.copyMs),
                      elapsed: (end - start) / 1000,
                      closedWithoutUpload: mediaTest.closedWithoutUpload.filter(
                        (f) => f.time >= start && f.time < end,
                      ),
                      decoded: decoded.length,
                      uploads: uploads.length,
                      copiedBytes: decoded.reduce((s, f) => s + f.bytes, 0),
                      skippedBeforeConversion: mediaTest.videoSkips
                        .filter((s) => s.time >= start && s.time < end)
                        .reduce((total, s) => total + s.count, 0),
                      readbackMs: decoded.reduce((s, f) => s + f.readbackMs, 0),
                      uploadMs: uploads.map((u) => u.ms),
                      frames: unique,
                      captureNotifications: frames.length,
                      captureError: observer.error,
                      observerClosedFrames: observer.closedFrames,
                      sampledBytesPerNotification: mediaTest.counter
                        ? mediaTest.counter.rect.width *
                          mediaTest.counter.rect.height *
                          4
                        : 0,
                      stages,
                      pixelStages,
                      gpuBindingChecks: globalThis.mediaGpuBindingChecks ?? [],
                      gpuDraws: (globalThis.mediaGpuDraws ?? []).filter(
                        (d) => d.time >= start && d.time < end,
                      ),
                      peakFrames: mediaTest.peakFrames,
                      peakTextures: mediaTest.peakTextures,
                      renderer: mediaTest.glInfo,
                      liveHandles: [...mediaTest.frameTextures].map(
                        (t) => t.name ?? 0,
                      ),
                    };
                  },
                  { start, width, height },
                );
                if (desktop) {
                  const d = await desktop.stop();
                  const frames = d.Frames.filter(
                    (f) =>
                      f.Epoch >= sample.startEpoch && f.Epoch < sample.endEpoch,
                  );
                  const unique = frames.filter(
                    (f, i) => i === 0 || f.Id !== frames[i - 1].Id,
                  );
                  if (desktop.sampling) assert.ok(unique.length > 2);
                  assert.ok(
                    unique.every(
                      (f, i) =>
                        f.Id >= 0 &&
                        f.Id < 14 * fps &&
                        (i === 0 || f.Id > unique[i - 1].Id),
                    ),
                  );
                  sample.desktop = {
                    bounds: desktop.bounds,
                    frames: unique,
                    samples: frames.length,
                    readMs: frames.map((f) => f.ReadMs),
                    sampling: desktop.sampling,
                    uniqueFps: desktop.sampling
                      ? unique.length / sample.elapsed
                      : null,
                  };
                }
                if (graphics) {
                  sample.graphicsTrace = await graphics.stop(
                    resolve(
                      work,
                      `${(process.env.MEDIA_BENCH_OUTPUT ?? "headed-fullhd.json").replace(/\.json$/, "")}-${browserName}-${height}-${fps}-${variant.name}-${round}-${capture}.trace.json.gz`,
                    ),
                  );
                  graphics = null;
                }
                assert.equal(sample.captureError, "");
                assert.ok(
                  sample.gpuBindingChecks.every(
                    (c) => c.trackedCanvasTarget === c.actualCanvasTarget,
                  ),
                  "GPU draw target tracking disagrees with WebGL",
                );
                assert.deepEqual(errors, []);
                if (capture && sample.frames.length <= 2)
                  await writeFile(
                    resolve(work, "gpu-fullhd-failure.json"),
                    JSON.stringify(
                      { variant: variant.name, source, ...sample },
                      null,
                      2,
                    ),
                  );
                if (capture) {
                  assert.ok(
                    sample.frames.length > 2,
                    JSON.stringify({
                      decoded: sample.decoded,
                      uploads: sample.uploads,
                      pixelStages: Object.fromEntries(
                        Object.entries(sample.pixelStages).map(([k, v]) => [
                          k,
                          v.length,
                        ]),
                      ),
                    }),
                  );
                  assert.ok(
                    sample.frames.every(
                      (f, i) =>
                        f.id >= 0 &&
                        f.id < 14 * fps &&
                        (i === 0 || f.id > sample.frames[i - 1].id),
                    ),
                    JSON.stringify(sample.frames),
                  );
                  assert.ok(
                    sample.frames.at(-1).id < 13 * fps,
                    "Measurement must finish before the fixture end",
                  );
                }
                if (variant.path === "gpu-texture") {
                  assert.equal(sample.copiedBytes, 0);
                  assert.ok(
                    sample.liveHandles.length > 0 &&
                      sample.liveHandles.every((id) => id > 0),
                  );
                  assert.equal(
                    await page.evaluate(
                      () =>
                        mediaTest.frameCanvases.size === 1 &&
                        mediaTest.frameCanvases.has(mediaTest.canvas),
                    ),
                    true,
                  );
                }
                if (
                  headed &&
                  !browser.foregroundReady &&
                  process.env.MEDIA_BENCH_FOREGROUND_GATE === "1"
                ) {
                  const token = randomUUID(),
                    title = "Media presentation probe " + token;
                  await page.evaluate(
                    (title) => (document.title = title),
                    title,
                  );
                  await writeFile(
                    resolve(work, "headed-window.json"),
                    JSON.stringify({ token, title, browser: browserName }),
                  );
                  for (;;) {
                    let ack;
                    try {
                      ack = JSON.parse(
                        await readFile(
                          resolve(work, "headed-foreground.json"),
                          "utf8",
                        ),
                      );
                    } catch {}
                    if (ack?.token === token) break;
                    await new Promise((r) => setTimeout(r, 200));
                  }
                  browser.foregroundReady = true;
                }
                if (headed && process.env.MEDIA_BENCH_DESKTOP !== "0")
                  desktop = await createDesktopProbe(browser, width, {
                    sampling: variant.desktop !== false,
                  });
                if (capture)
                  await page.evaluate(async () => {
                    await mediaTest.counter.rpc("stop");
                    mediaTest.counter.stream
                      .getTracks()
                      .forEach((t) => t.stop());
                    mediaTest.counter.worker.terminate();
                  });
                await page.mouse.click(90, 64);
                await page.waitForFunction(
                  () =>
                    mediaTest.workers.every((w) => w.closed) &&
                    mediaTest.frameTextures.size === 0 &&
                    mediaTest.transferredFrames.size === 0 &&
                    mediaTest.contexts.every((c) => c.state === "closed"),
                );
                const gaps = sample.frames
                    .slice(1)
                    .map((f, i) => f.time - sample.frames[i].time),
                  missed = sample.frames
                    .slice(1)
                    .reduce((s, f, i) => s + f.id - sample.frames[i].id - 1, 0),
                  row = {
                    browser: browserName,
                    version: browser.version(),
                    build: composeBuild,
                    headed,
                    variant: variant.name,
                    source,
                    width,
                    height,
                    sourceFps: fps,
                    round,
                    capture,
                    capturePolicy: capture ? "per-paint" : "disabled",
                    captureBufferSize: variant.captureBuffer ?? 1,
                    profiling,
                    ...sample,
                    canvasCaptureUniqueFps: capture
                      ? sample.frames.length / sample.elapsed
                      : null,
                    desktopObservedUniqueFps: sample.desktop?.uniqueFps ?? null,
                    importFps: sample.uploads / sample.elapsed,
                    decodedFps: sample.decoded / sample.elapsed,
                    canvasCaptureSkippedIds: capture ? missed : null,
                    canvasCaptureGapMsP95: q(gaps, 0.95),
                    canvasCaptureGapMsMax: q(gaps, 1),
                    mainTaskMs:
                      (after.TaskDuration - before.TaskDuration) * 1000,
                    mainScriptMs:
                      (after.ScriptDuration - before.ScriptDuration) * 1000,
                    uploadMsP50: q(sample.uploadMs, 0.5),
                    uploadMsP95: q(sample.uploadMs, 0.95),
                    disposed: true,
                  };
                results.push(row);
                await writeFile(
                  resolve(
                    work,
                    process.env.MEDIA_BENCH_OUTPUT ??
                      "gpu-fullhd-comparison.json",
                  ),
                  JSON.stringify(
                    {
                      measurementSchema: 3,
                      build: composeBuild,
                      viewport: { width: 1952, height: 1360 },
                      videoDisplayArea: {
                        x: 16,
                        y: 88,
                        width: 1920,
                        height: 1080,
                      },
                      method: headed
                        ? "Headed browser, calibrated physical 1920x1080 desktop bounds and foreground PID; source video IDs correlated across GL commands, desktop compositor samples and optional canvas capture; paired observer controls"
                        : "Headless canvas frame-ID observation; no physical desktop observation",
                      results,
                    },
                    null,
                    2,
                  ),
                );
                console.info(
                  JSON.stringify({
                    browser: browserName,
                    height,
                    fps,
                    variant: variant.name,
                    round,
                    capture,
                    canvasCaptureFps: row.canvasCaptureUniqueFps,
                    desktopObservedFps: row.desktopObservedUniqueFps,
                    imported: row.importFps,
                    missed,
                    cpu: row.mainTaskMs,
                  }),
                );
              } finally {
                if (graphics)
                  await graphics.stop(
                    resolve(work, "headed-failed.trace.json.gz"),
                    { validate: false },
                  );
                await desktop?.close();
                page.off("pageerror", onPageError);
                if (!headed) await page.close();
              }
            }
        }
    } finally {
      await browser.close();
    }
  }
} finally {
  await site.close();
}
