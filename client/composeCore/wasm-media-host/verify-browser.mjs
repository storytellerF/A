/* This is a private project. All rights reserved. */
import { runCase, launchBrowser, ownSite } from "./verification-runner.mjs";
import { getReportDirectory } from "./verification-reports.mjs";
import assert from "node:assert/strict";
import { writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { work, startSite, chromiumBinaries } from "./verification-site.mjs";
const { chromium } = await import(
  pathToFileURL(resolve(work, "browser/node_modules/playwright/index.mjs"))
);
await runCase("verify-browser", async () => {
  const site = ownSite(await startSite());
  const { origin } = site;
  const results = [];
  try {
    for (const [name, executablePath] of Object.entries(chromiumBinaries)) {
      await runCase(`browser ${name}`, async () => {
        const browser = await launchBrowser(chromium, {
          executablePath,
          headless: true,
        });
        try {
          for (const [kind, src] of [
            ["av", "fixture.webm"],
            ["audio", "fixture.wav"],
            ["mp3", "fixture.mp3"],
            ["silent", "silent.webm"],
            ["mp4", "fixture.mp4"],
          ]) {
            await runCase(`playback ${kind}`, async () => {
              const page = await browser.newPage({ locale: "en-US" });
              const errors = [];
              page.on("pageerror", (e) => errors.push(e.message));
              await page.goto(`${origin}/?src=/${src}`);
              await page.waitForFunction(
                () =>
                  window.states.at(-1)?.status === "paused" ||
                  window.states.at(-1)?.status === "error",
              );
              assert.equal(
                await page.evaluate(() => states.at(-1).status),
                "paused",
              );
              await page.click("#play");
              await page.waitForFunction(() => states.at(-1)?.position > 1.5);
              const measure = await page.evaluate(async () => {
                let rms = null;
                if (host.context) {
                  const analyser = host.context.createAnalyser();
                  host.gain.connect(analyser);
                  const samples = new Float32Array(analyser.fftSize);
                  await new Promise((r) => setTimeout(r, 100));
                  analyser.getFloatTimeDomainData(samples);
                  rms = Math.sqrt(
                    samples.reduce((a, v) => a + v * v, 0) / samples.length,
                  );
                  host.gain.disconnect(analyser);
                }
                return {
                  state: states.at(-1),
                  rms,
                  uniqueFrames: new Set(hashes).size,
                  metadata: host.metadata,
                  context: host.context?.state ?? null,
                };
              });
              if (!["audio", "mp3"].includes(kind)) {
                assert.ok(measure.uniqueFrames > 15);
                assert.ok(measure.state.maxSkew < 0.15);
              } else assert.equal(measure.uniqueFrames, 0);
              if (kind !== "silent") assert.ok(measure.rms > 0.01);
              else assert.equal(measure.context, null);
              await page.evaluate(() => host.pause());
              await page.waitForFunction(
                () => states.at(-1)?.status === "paused",
              );
              const paused = await page.evaluate(() => states.at(-1).position);
              await page.waitForTimeout(200);
              assert.equal(
                await page.evaluate(() => host.currentTime()),
                paused,
              );
              await page.evaluate(() => host.seek(3));
              assert.equal(
                await page.evaluate(() => states.at(-1).position),
                3,
              );
              await page.click("#play");
              await page.waitForFunction(() => states.at(-1)?.position > 3.4);
              if (kind !== "silent") {
                const rms = await page.evaluate(async () => {
                  host.setVolume(0.2, true);
                  const a = host.context.createAnalyser();
                  host.gain.connect(a);
                  await new Promise((r) => setTimeout(r, 100));
                  const b = new Float32Array(a.fftSize);
                  a.getFloatTimeDomainData(b);
                  host.gain.disconnect(a);
                  return Math.sqrt(b.reduce((s, x) => s + x * x, 0) / b.length);
                });
                assert.equal(rms, 0);
                await page.evaluate(() => host.setVolume(1, false));
              }
              await page.evaluate(() => host.seek(5.6));
              await page.waitForFunction(
                () => states.at(-1)?.status === "ended",
              );
              await page.click("#play");
              await page.waitForFunction(
                () =>
                  states.at(-1)?.status === "playing" &&
                  states.at(-1).position > 0.3,
              );
              assert.ok(await page.evaluate(() => host.currentTime() < 1));
              const closed = await page.evaluate(async () => {
                const audio = host.context;
                await host.close();
                const count = states.length;
                await new Promise((r) => setTimeout(r, 150));
                return {
                  status: host.status,
                  nodes: host.nodes.size,
                  worker: host.worker,
                  context: audio?.state ?? null,
                  activeContext: host.context,
                  extraStates: states.length - count,
                };
              });
              assert.equal(closed.status, "closed");
              assert.equal(closed.nodes, 0);
              assert.equal(closed.worker, null);
              assert.equal(closed.activeContext, null);
              assert.equal(closed.extraStates, 0);
              if (kind !== "silent") assert.equal(closed.context, "closed");
              assert.deepEqual(errors, []);
              results.push({
                browser: name,
                version: browser.version(),
                kind,
                ...measure,
                closed,
              });
              await page.close();
            });
          }
          await runCase("native baseline", async () => {
            const baselinePage = await browser.newPage({ locale: "en-US" });
            await baselinePage.goto(origin);
            await baselinePage.evaluate(async () => {
              await host.close();
              const video = document.createElement("video");
              video.src = "/fixture.webm";
              video.controls = true;
              document.body.append(video);
              window.baselineVideo = video;
              window.baselineFrames = 0;
              window.baselineCallbacks = 0;
              const frame = (_now, metadata) => {
                baselineCallbacks++;
                baselineFrames = metadata.presentedFrames;
                video.requestVideoFrameCallback(frame);
              };
              video.requestVideoFrameCallback(frame);
              document.querySelector("#play").onclick = () => video.play();
            });
            await baselinePage.click("#play");
            await baselinePage.waitForFunction(
              () => baselineVideo.currentTime > 1.5,
            );
            const baseline = await baselinePage.evaluate(() => ({
              time: baselineVideo.currentTime,
              frames: baselineFrames,
              callbacks: baselineCallbacks,
              dropped:
                baselineVideo.getVideoPlaybackQuality().droppedVideoFrames,
            }));
            assert.ok(
              baseline.frames > 30,
              JSON.stringify({ browser: name, ...baseline }),
            );
            results.push({
              browser: name,
              kind: "native-video-baseline",
              version: browser.version(),
              ...baseline,
            });
            await baselinePage.close();
          });
          await runCase("unsupported codec", async () => {
            const codecPage = await browser.newPage({ locale: "en-US" });
            await codecPage.goto(`${origin}/?src=/unsupported.mov`);
            await codecPage.waitForFunction(
              () => states.at(-1)?.status === "error",
            );
            assert.ok(
              await codecPage.evaluate(() =>
                states.at(-1).error.includes("Unsupported browser codec:"),
              ),
            );
            assert.equal(await codecPage.evaluate(() => host.worker), null);
            await codecPage.evaluate(() => host.close());
            await codecPage.close();
            results.push({
              browser: name,
              version: browser.version(),
              kind: "unsupported-prores",
              status: "error",
            });
          });
          await runCase("missing source", async () => {
            const page = await browser.newPage({ locale: "en-US" });
            await page.goto(`${origin}/?src=/missing.webm`);
            await page.waitForFunction(() => states.at(-1)?.status === "error");
            assert.ok(
              await page.evaluate(() => states.at(-1).error.includes("CORS")),
            );
            await page.evaluate(() => host.close());
            await page.close();
            results.push({
              browser: name,
              version: browser.version(),
              kind: "missing-source",
              status: "error",
            });
          });
        } finally {
          await browser.close();
        }
      });
    }
    console.log(JSON.stringify(results, null, 2));
    await writeFile(
      resolve(await getReportDirectory(), "browser-results.json"),
      JSON.stringify(results, null, 2),
    );
  } finally {
    await site.close();
  }
});
