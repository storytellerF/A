/* This is a private project. All rights reserved. */
import {
  runCase,
  launchFirefoxDriver,
  ownSite,
} from "./verification-runner.mjs";
import { getReportDirectory } from "./verification-reports.mjs";
import assert from "node:assert/strict";
import { resolve } from "node:path";
import { mkdir, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";
import { work, startSite } from "./verification-site.mjs";
const selenium = await import(
  pathToFileURL(
    resolve(work, "browser/node_modules/selenium-webdriver/index.js"),
  )
);
const firefox = await import(
  pathToFileURL(
    resolve(work, "browser/node_modules/selenium-webdriver/firefox.js"),
  )
);
assert.ok(
  process.env.MEDIA_GECKODRIVER,
  "Set MEDIA_GECKODRIVER to an existing geckodriver executable.",
);
const profileRoot = resolve(work, "firefox-profiles");
await mkdir(profileRoot, { recursive: true });
await runCase("verify-firefox", async () => {
  const site = ownSite(await startSite());
  let driver;
  const results = [];
  const execute = (script) => driver.executeScript(script);
  try {
    driver = await launchFirefoxDriver(selenium, {
      executable: process.env.MEDIA_GECKODRIVER,
      profileRoot,
      options: new firefox.Options()
        .addArguments("-headless")
        .setPreference("intl.accept_languages", "en-US,en"),
    });
    const browserVersion = (await driver.getCapabilities()).get(
      "browserVersion",
    );
    for (const [kind, src] of [
      ["av", "fixture.webm"],
      ["audio", "fixture.wav"],
      ["mp3", "fixture.mp3"],
      ["silent", "silent.webm"],
      ["mp4", "fixture.mp4"],
    ]) {
      await runCase(`playback ${kind}`, async () => {
        await driver.get(`${site.origin}/?src=/${src}`);
        await driver.wait(
          () =>
            execute(
              "return window.states?.at(-1)?.status === 'paused' || window.states?.at(-1)?.status === 'error'",
            ),
          10000,
        );
        const initial = await execute("return states.at(-1)");
        assert.equal(initial.status, "paused", initial.error);
        await driver.findElement(selenium.By.id("play")).click();
        await driver.wait(
          () => execute("return states.at(-1).position > 1.5"),
          10000,
        );
        const measure =
          await driver.executeAsyncScript(`const done=arguments[arguments.length-1];
      (async()=>{let rms=null;if(host.context){const a=host.context.createAnalyser();host.gain.connect(a);
        await new Promise(r=>setTimeout(r,100));const b=new Float32Array(a.fftSize);a.getFloatTimeDomainData(b);
        rms=Math.sqrt(b.reduce((s,v)=>s+v*v,0)/b.length);host.gain.disconnect(a);}
        done({state:states.at(-1),rms,uniqueFrames:new Set(hashes).size,metadata:host.metadata});})()`);
        if (!["audio", "mp3"].includes(kind)) {
          assert.ok(measure.uniqueFrames > 15);
          assert.ok(measure.state.maxSkew < 0.15);
        }
        if (kind !== "silent") assert.ok(measure.rms > 0.01);
        await execute("host.pause()");
        await driver.wait(
          () => execute("return states.at(-1).status==='paused'"),
          10000,
        );
        const paused = await execute("return host.currentTime()");
        await execute("host.setVolume(0.2,true)");
        assert.equal(await execute("return host.gain?.gain.value ?? 0"), 0);
        await driver.executeAsyncScript(
          "const done=arguments[arguments.length-1];host.seek(3).then(done)",
        );
        assert.equal(await execute("return host.currentTime()"), 3);
        await driver.findElement(selenium.By.id("play")).click();
        await driver.wait(
          () => execute("return states.at(-1).position>3.4"),
          10000,
        );
        await driver.executeAsyncScript(
          "const done=arguments[arguments.length-1];host.close().then(done)",
        );
        assert.equal(await execute("return host.nodes.size"), 0);
        assert.equal(
          await execute('return host.context?.state??"closed"'),
          "closed",
        );
        results.push({
          browser: "firefox",
          version: browserVersion,
          kind,
          paused,
          ...measure,
        });
      });
    }
    await writeFile(
      resolve(await getReportDirectory(), "firefox-results.json"),
      JSON.stringify(results, null, 2),
    );
    console.log(JSON.stringify(results, null, 2));
  } finally {
    if (driver) await driver.quit();
    await site.close();
  }
});
