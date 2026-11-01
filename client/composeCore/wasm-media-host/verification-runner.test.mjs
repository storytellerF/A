/* This is a private project. All rights reserved. */
import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";

const runner = new URL("./verification-runner.mjs", import.meta.url).href;
const reports = new URL("./verification-reports.mjs", import.meta.url).href;

async function executeFixture(source) {
  const environment = { ...process.env };
  delete environment.NODE_TEST_CONTEXT;
  const child = spawn(
    process.execPath,
    ["--test-reporter=tap", "--input-type=module", "--eval", source],
    { env: environment, timeout: 5000 },
  );
  const output = [];
  child.stdout.on("data", (chunk) => output.push(chunk));
  child.stderr.on("data", (chunk) => output.push(chunk));
  const code = await new Promise((done, reject) => {
    child.once("error", reject);
    child.once("close", done);
  });
  return { code, report: Buffer.concat(output).toString() };
}

test("a failed child releases its browser and the next contract still runs", async () => {
  const { code, report } = await executeFixture(`
    import { runCase, launchBrowser } from ${JSON.stringify(runner)};
    import assert from "node:assert/strict";
    let pageClosed = false;
    let traceSaved = false;
    const page = {
      on: () => {}, evaluate: async () => ({}), screenshot: async () => {},
      context: () => ({tracing: {start: async () => {}, stop: async () => { traceSaved = true; }}}),
      close: async () => { assert.equal(traceSaved, true); pageClosed = true; },
    };
    const browser = { closed: false, newPage: async () => page,
      close: async () => { assert.equal(pageClosed, true); browser.closed = true; } };
    await runCase('isolation fixture', async () => {
      await runCase('first failure', async () => {
        const owned = await launchBrowser({launchServer: async () => ({wsEndpoint: () => "fake", close: browser.close.bind(browser), kill: async () => {browser.closed = true;}}), connect: async () => browser}, {});
        await owned.newPage();
        throw new Error('original assertion');
      });
      await runCase('later contract', async () => { assert.equal(browser.closed, true); });
    });
  `);
  assert.equal(code, 1);
  assert.match(report, /original assertion/);
  assert.match(report, /ok 2 - later contract/);
  assert.doesNotMatch(report, /cancelledByParent/);
});

for (const scenario of [
  "state-hang",
  "state-late-reject",
  "screenshot-hang",
  "trace-hang",
  "page-close-fail",
  "page-close-hang",
  "browser-close-fail",
  "browser-close-hang",
  "browser-active-handle",
  "cleanup-only",
  "driver-hang",
  "driver-active-handle",
  "site-close-fail",
]) {
  test(`${scenario} preserves the case outcome and allows the next case`, async () => {
    const { code, report } = await executeFixture(`
      import assert from 'node:assert/strict';
      import { readdir, readFile } from 'node:fs/promises';
      import { resolve } from 'node:path';
      import { runCase, launchBrowser, registerDriver, ownSite } from ${JSON.stringify(runner)};
      import { getReportDirectory } from ${JSON.stringify(reports)};
      const scenario = ${JSON.stringify(scenario)};
      const events = [];
      let nativeHandle;
      if (scenario.endsWith('active-handle')) nativeHandle = setInterval(() => {}, 1000);
      const never = () => new Promise(() => {});
      const page = {
        on() {},
        evaluate: async () => scenario === 'state-hang' ? never() : scenario === 'state-late-reject' ? new Promise((_, reject) => setTimeout(() => reject(new Error('LATE_DIAGNOSTIC_ERROR')), 100)) : {},
        screenshot: async () => scenario === 'screenshot-hang' ? never() : undefined,
        context: () => ({tracing: {
          start: async () => {},
          stop: async () => { events.push('trace-stop'); return scenario === 'trace-hang' ? never() : undefined; },
        }}),
        close: async () => {
          events.push('page-close');
          if (scenario === 'page-close-hang') return never();
          if (['page-close-fail','cleanup-only'].includes(scenario)) throw new Error('PAGE_CLOSE_ERROR');
        },
      };
      const nativeBrowser = { newPage: async () => page, close: async () => {
        events.push('browser-close');
        if (['browser-close-hang','browser-active-handle'].includes(scenario)) return never();
        if (scenario === 'browser-close-fail') throw new Error('BROWSER_CLOSE_ERROR');
      }};
      await runCase('fixture', async () => {
        await runCase('first failure', async () => {
          if (scenario.startsWith('driver-')) {
            const driver = { takeScreenshot: never, getPageSource: async () => '<xml/>',
              executeScript: async () => ({}), quit: async () => { events.push('driver-quit'); return never(); } };
            registerDriver(driver, async () => { events.push('driver-force'); clearInterval(nativeHandle); });
            throw new Error('ORIGINAL_ASSERTION');
          }
          if (scenario === 'site-close-fail') {
            const site = ownSite({close: async () => { events.push('site-close'); throw new Error('SITE_CLOSE_ERROR'); }});
            try { throw new Error('ORIGINAL_ASSERTION'); }
            finally { await site.close(); }
          }
          const browser = await launchBrowser({launchServer: async () => ({wsEndpoint: () => 'fake', close: nativeBrowser.close.bind(nativeBrowser), kill: async () => {events.push('browser-force'); clearInterval(nativeHandle);}}), connect: async () => nativeBrowser}, {});
          const owned = await browser.newPage();
          try {
            if (scenario !== 'cleanup-only') throw new Error('ORIGINAL_ASSERTION');
          } finally {
            await owned.close();
            await browser.close();
          }
        }, { diagnosticTimeoutMs: 30, cleanupTimeoutMs: 30 });
        await runCase('later contract', async () => {
          const expected = scenario.startsWith('driver-') ? 'driver-quit' : scenario === 'site-close-fail' ? 'site-close' : 'browser-close';
          assert.ok(events.includes(expected));
          if (scenario === 'state-hang' || scenario === 'screenshot-hang') assert.ok(events.includes('trace-stop'));
          if (scenario.endsWith('active-handle')) assert.ok(events.some(e => e.endsWith('-force')));
          const artifacts = resolve(await getReportDirectory(), 'artifacts');
          const folder = (await readdir(artifacts)).find(name => name.endsWith('first-failure'));
          const failure = await readFile(resolve(artifacts, folder, 'failure.txt'), 'utf8');
          assert.match(failure, scenario === 'cleanup-only' ? /Fixture cleanup failed/ : /ORIGINAL_ASSERTION/);
          if (scenario.includes('close') || scenario === 'cleanup-only' || scenario.startsWith('driver-')) {
            const additional = JSON.parse(await readFile(resolve(artifacts, folder, 'cleanup-errors.json'), 'utf8'));
            assert.ok(additional.length > 0);
          }
        });
      });
    `);
    assert.equal(code, 1, report);
    assert.match(report, /ok 2 - later contract/);
    assert.doesNotMatch(report, /cancelledByParent/);
  });
}
