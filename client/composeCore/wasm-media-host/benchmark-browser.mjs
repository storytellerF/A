/* This is a private project. All rights reserved. */
import { startSite, work, chromiumBinaries } from "./verification-site.mjs";
import { resolve } from "node:path";
import { pathToFileURL } from "node:url";
import { writeFile } from "node:fs/promises";
import assert from "node:assert/strict";
const { chromium } = await import(
  pathToFileURL(resolve(work, "browser/node_modules/playwright/index.mjs"))
);
const site = await startSite();
let browser;
const results = [];
try {
  browser = await chromium.launch({
    executablePath: chromiumBinaries.chrome,
    headless: true,
  });
  for (const src of ["fixture.mp4", "fixture-hd.mp4", "fixture-fhd.mp4"]) {
    const page = await browser.newPage({ locale: "en-US" });
    await page.goto(`${site.origin}/?src=/${src}`);
    await page.waitForFunction(
      () =>
        states.at(-1)?.status === "paused" || states.at(-1)?.status === "error",
    );
    assert.equal(await page.evaluate(() => states.at(-1).status), "paused");
    await page.click("#play");
    await page.waitForFunction(() => states.at(-1)?.position > 2.5);
    const measured = await page.evaluate(() => ({
      state: states.at(-1),
      uniqueFrames: new Set(hashes).size,
    }));
    assert.ok(measured.uniqueFrames > 10);
    assert.ok(measured.state.position > 2.5);
    results.push({ source: src, version: browser.version(), host: measured });
    await page.evaluate(() => host.close());
    await page.reload();
    await page.evaluate(async () => {
      await host.close();
      window.v = document.createElement("video");
      v.src = new URL(location.href).searchParams.get("src");
      document.body.append(v);
      window.frames = 0;
      const tick = () => {
        frames++;
        v.requestVideoFrameCallback(tick);
      };
      v.requestVideoFrameCallback(tick);
      document.querySelector("#play").onclick = () => v.play();
    });
    await page.click("#play");
    await page.waitForFunction(() => v.currentTime > 2.5);
    results.at(-1).native = await page.evaluate(() => ({
      frames,
      time: v.currentTime,
      dropped: v.getVideoPlaybackQuality().droppedVideoFrames,
    }));
    await page.close();
  }
  await writeFile(
    resolve(work, "performance-results.json"),
    JSON.stringify(results, null, 2),
  );
  console.log(JSON.stringify(results, null, 2));
} finally {
  if (browser) await browser.close();
  site.close();
}
