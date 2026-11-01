/* This is a private project. All rights reserved. */
import test from "node:test";
import { spawn } from "node:child_process";
import { pathToFileURL } from "node:url";
import { runBrowserBatches } from "./run-browser-tests.mjs";
import assert from "node:assert/strict";
import {
  mkdir,
  mkdtemp,
  readFile,
  readdir,
  rm,
  writeFile,
} from "node:fs/promises";
import { resolve } from "node:path";
import {
  collectReportRun,
  createReportRun,
  writeRunManifest,
} from "./verification-reports.mjs";

async function fixture(context) {
  const parent = resolve(import.meta.dirname, "build/report-tests");
  await mkdir(parent, { recursive: true });
  const directory = await mkdtemp(resolve(parent, "case-"));
  context.after(() => rm(directory, { recursive: true, force: true }));
  return {
    raw: resolve(directory, "raw"),
    collected: resolve(directory, "collected"),
  };
}

test("collection publishes a separate snapshot only after raw reports are finished", async (context) => {
  const { raw, collected } = await fixture(context);
  const run = await createReportRun(raw);
  const batch = resolve(run, "host");
  await mkdir(batch);
  await writeFile(resolve(batch, "junit.xml"), '<testsuite failures="1"/>');
  await writeFile(resolve(batch, "page-trace.zip"), "synthetic trace");
  await writeRunManifest(run, [{ name: "host", code: 1, signal: null }]);
  await assert.rejects(readdir(collected), { code: "ENOENT" });
  const published = await collectReportRun(run, collected);
  assert.equal(
    await readFile(resolve(published, "host/page-trace.zip"), "utf8"),
    "synthetic trace",
  );
  assert.equal(
    JSON.parse(await readFile(resolve(published, "manifest.json"), "utf8"))
      .batches[0].code,
    1,
  );
  await writeFile(resolve(batch, "page-trace.zip"), "later raw change");
  assert.equal(
    await readFile(resolve(published, "host/page-trace.zip"), "utf8"),
    "synthetic trace",
  );
});

test("a new run excludes previous reports and a collection failure preserves previous snapshots", async (context) => {
  const { raw, collected } = await fixture(context);
  const first = await createReportRun(raw);
  await writeFile(resolve(first, "old.xml"), "old report");
  const firstPublished = await collectReportRun(first, collected);
  const second = await createReportRun(raw);
  await writeFile(resolve(second, "new.xml"), "new report");
  const secondPublished = await collectReportRun(second, collected);
  assert.deepEqual(await readdir(secondPublished), ["new.xml"]);
  await assert.rejects(
    collectReportRun(resolve(raw, "missing-run"), collected),
    { code: "ENOENT" },
  );
  assert.equal(
    await readFile(resolve(firstPublished, "old.xml"), "utf8"),
    "old report",
  );
  assert.equal(await readFile(resolve(first, "old.xml"), "utf8"), "old report");
});

test("failed batches are collected only after later batches finish", async (context) => {
  const { raw, collected } = await fixture(context);
  const source = resolve(raw, "fixtures");
  await mkdir(source, { recursive: true });
  for (const [name, failure] of [
    ["first", true],
    ["second", false],
  ]) {
    await writeFile(
      resolve(source, `verify-${name}.mjs`),
      `
      import test from 'node:test';
      import assert from 'node:assert/strict';
      import { stat, writeFile } from 'node:fs/promises';
      import { resolve } from 'node:path';
      test('synthetic ${name} batch', async () => {
        await assert.rejects(stat(${JSON.stringify(collected)}), { code: 'ENOENT' });
        await writeFile(resolve(process.env.MEDIA_REPORT_DIR, 'marker.json'), '${name}');
        assert.equal(${failure}, false, 'expected synthetic failure');
      });
    `,
    );
  }
  const result = await runBrowserBatches(
    [
      ["first", ["first"], {}],
      ["second", ["second"], {}],
    ],
    { sourceDirectory: source, raw, destination: collected },
  );
  assert.equal(result.failed, true);
  assert.deepEqual(
    result.batches.map((b) => b.code),
    [1, 0],
  );
  assert.equal(
    await readFile(resolve(result.collected, "second/marker.json"), "utf8"),
    "second",
  );
  for (const name of ["first", "second"]) {
    assert.match(
      await readFile(resolve(result.collected, name, "junit.xml"), "utf8"),
      /testsuites/,
    );
  }
});

for (const outcome of ["success", "exit-code", "signal", "import-error", "assertion"]) {
  test(`${outcome} keeps collected JUnit consistent with worker exit`, async (context) => {
    const { raw, collected } = await fixture(context);
    const source = resolve(raw, "fixtures");
    await mkdir(source, { recursive: true });
    const action = {
      success: "",
      "exit-code": "process.exitCode = 17;",
      // The exit hook runs after the reporter has drained its output stream.
      signal: "process.once('exit', () => process.kill(process.pid, 'SIGTERM'));",
      "import-error": "throw new Error('IMPORT_FAILURE');",
      assertion: "",
    }[outcome];
    await writeFile(
      resolve(source, "verify-outcome.mjs"),
      `import test from 'node:test';
       await test('original case', () => {
         if (${JSON.stringify(outcome)} === 'assertion') throw new Error('ORIGINAL_ASSERTION');
       });
       ${action}`,
    );
    const result = await runBrowserBatches(
      [["outcome", ["outcome"], {}]],
      { sourceDirectory: source, raw, destination: collected },
    );
    const suite = result.batches[0].suites[0];
    const report = await readFile(resolve(result.collected, "outcome/junit.xml"), "utf8");
    assert.equal(result.failed, outcome !== "success");
    assert.equal(suite.termination, undefined);
    assert.equal((report.match(/<testcase name="original case"/g) ?? []).length, 1);
    if (outcome === "success") {
      assert.equal(suite.code, 0);
      assert.doesNotMatch(report, /worker lifecycle|<failure/);
    } else {
      assert.match(report, /worker lifecycle/);
      const reason = suite.signal
        ? `Worker exited with signal ${suite.signal}`
        : `Worker exited with code ${suite.code}`;
      assert.ok(report.includes(reason));
      assert.equal(suite.error, reason);
      if (outcome === "exit-code") assert.equal(suite.code, 17);
      if (outcome === "assertion") assert.match(report, /ORIGINAL_ASSERTION/);
      if (outcome === "import-error") {
        assert.match(
          await readFile(resolve(result.collected, "outcome/workers/outcome/stderr.log"), "utf8"),
          /IMPORT_FAILURE/,
        );
      }
    }
  });
}

for (const reason of [
  "exit-timeout",
  "execution-timeout",
  "root-exited-pipes",
]) {
  test(
    `${reason} releases an owned native child before the next suite`,
    { timeout: 15000 },
    async (context) => {
      const { raw, collected } = await fixture(context);
      const source = resolve(raw, "fixtures");
      await mkdir(source, { recursive: true });
      const nativePid = resolve(source, "native.pid");
      const runner = pathToFileURL(
        resolve(import.meta.dirname, "verification-runner.mjs"),
      ).href;
      const sentinel = spawn(
        process.execPath,
        ["-e", "setInterval(()=>{},1000)"],
        { stdio: "ignore", windowsHide: true },
      );
      context.after(() => sentinel.kill());
      await writeFile(
        resolve(source, "verify-stuck.mjs"),
        `
      import { spawn } from 'node:child_process';
      import {writeFile} from 'node:fs/promises';
      import {runCase,launchBrowser} from ${JSON.stringify(runner)};
      const native = spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{
        detached:true,stdio:['ignore',process.stdout,process.stderr],windowsHide:true});
      await writeFile(${JSON.stringify(nativePid)},String(native.pid));
      if (${JSON.stringify(reason)} === 'root-exited-pipes') process.exit(17);
      const never = () => new Promise(()=>{});
      if (${JSON.stringify(reason)} === 'execution-timeout') { while (true) {} }
      await runCase('original case',async()=>{
        await launchBrowser({launchServer:async()=>({wsEndpoint:()=>'',close:never,kill:never}),
          connect:async()=>({newPage:async()=>{}})},{});
        throw new Error('ORIGINAL_ASSERTION');
      },{diagnosticTimeoutMs:30,cleanupTimeoutMs:30});
    `,
      );
      await writeFile(
        resolve(source, "verify-next.mjs"),
        `
      import assert from 'node:assert/strict';
      import test from 'node:test';
      import {readFile,writeFile} from 'node:fs/promises';
      await test('native resource is gone',async()=>{
        const pid=Number(await readFile(${JSON.stringify(nativePid)},'utf8'));
        assert.throws(()=>process.kill(pid,0), {code:'ESRCH'});
        await writeFile(${JSON.stringify(resolve(source, "next.marker"))},'executed');
      });
    `,
      );
      const result = await runBrowserBatches(
        [["isolation", ["stuck", "next"], {}]],
        {
          sourceDirectory: source,
          raw,
          destination: collected,
          workerBudgets: {
            executionTimeoutMs: 2000,
            exitTimeoutMs: 50,
            killTimeoutMs: 2000,
          },
        },
      );
      assert.equal(result.failed, true);
      assert.equal(
        result.batches[0].suites[0].termination,
        reason === "root-exited-pipes" ? "exit-timeout" : reason,
      );
      if (reason === "root-exited-pipes")
        assert.equal(result.batches[0].suites[0].code, 17);
      assert.equal(result.batches[0].suites[1].code, 0);
      assert.equal(
        await readFile(resolve(source, "next.marker"), "utf8"),
        "executed",
      );
      assert.doesNotThrow(() => process.kill(sentinel.pid, 0));
      const report = await readFile(
        resolve(result.collected, "isolation/junit.xml"),
        "utf8",
      );
      assert.match(report, /worker lifecycle/);
      assert.match(
        report,
        new RegExp(reason === "root-exited-pipes" ? "exit-timeout" : reason),
      );
      if (reason === "exit-timeout") {
        const children = await readdir(resolve(result.raw, "isolation"));
        const suite = children.find((name) => name.startsWith("verify-stuck-"));
        const artifacts = resolve(result.raw, "isolation", suite, "artifacts");
        const entry = (await readdir(artifacts))[0];
        assert.match(
          await readFile(resolve(artifacts, entry, "failure.txt"), "utf8"),
          /ORIGINAL_ASSERTION/,
        );
      }
    },
  );
}

test(
  "container owner releases detached descendants when its supervisor disappears",
  { timeout: 15000 },
  async (context) => {
    const { raw, collected } = await fixture(context);
    const source = resolve(raw, "fixtures");
    await mkdir(source, { recursive: true });
    const nativePid = resolve(source, "native.pid");
    const api = pathToFileURL(
      resolve(import.meta.dirname, "run-browser-tests.mjs"),
    ).href;
    await writeFile(
      resolve(source, "verify-stuck.mjs"),
      `
    import {spawn} from 'node:child_process';
    import {writeFile} from 'node:fs/promises';
    const child=spawn(process.execPath,['-e','setInterval(()=>{},1000)'],{detached:true,stdio:'ignore',windowsHide:true});
    await writeFile(${JSON.stringify(nativePid)},String(child.pid));
    await new Promise(()=>{});
  `,
    );
    await writeFile(
      resolve(source, "supervisor.mjs"),
      `
    import {runBrowserBatches} from ${JSON.stringify(api)};
    import {existsSync} from 'node:fs';
    runBrowserBatches([['case',['stuck'],{}]],{sourceDirectory:import.meta.dirname,raw:${JSON.stringify(raw)},destination:${JSON.stringify(collected)}}).catch(()=>{});
    setInterval(()=>{if(existsSync(${JSON.stringify(nativePid)}))process.exit(23)},10);
  `,
    );
    const environment = { ...process.env };
    delete environment.NODE_TEST_CONTEXT;
    const parent = spawn(
      process.execPath,
      [resolve(source, "supervisor.mjs")],
      { stdio: "ignore", windowsHide: true, env: environment },
    );
    let ownerPid;
    context.after(() => {
      parent.kill();
      if (ownerPid)
        try {
          process.kill(ownerPid, "SIGTERM");
        } catch (error) {
          if (error.code !== "ESRCH") throw error;
        }
    });
    const exit = await new Promise((done, reject) => {
      parent.once("error", reject);
      parent.once("exit", done);
    });
    assert.equal(exit, 23);
    const run = (await readdir(raw)).find((name) => name.startsWith("run-"));
    const journal = resolve(raw, run, "case/workers/stuck/container.jsonl");
    let state;
    const pid = Number(await readFile(nativePid, "utf8"));
    const gone = (pid) => {
      try {
        process.kill(pid, 0);
        return false;
      } catch (error) {
        if (error.code === "ESRCH") return true;
        throw error;
      }
    };
    const deadline = performance.now() + 5000;
    do {
      const text = await readFile(journal, "utf8");
      const last = text.lastIndexOf("\n");
      if (last >= 0) state = JSON.parse(text.slice(0, last).split("\n").at(-1));
      ownerPid = state?.owner_pid;
      if (state?.phase === "released") break;
      // On Windows the owner may be killed together with its supervisor.
      // KILL_ON_JOB_CLOSE must still release the root and detached child.
      if (
        process.platform === "win32" &&
        ownerPid &&
        gone(ownerPid) &&
        gone(state.root_pid) &&
        gone(pid)
      )
        break;
      await new Promise((done) => setTimeout(done, 20));
    } while (performance.now() < deadline);
    if (state.phase === "released") {
      assert.equal(state.active, 0);
      assert.equal(state.forced, true);
    } else {
      assert.equal(process.platform, "win32");
      assert.ok(gone(ownerPid));
    }
    assert.ok(gone(state.root_pid));
    assert.ok(gone(pid));
    ownerPid = undefined;
  },
);
