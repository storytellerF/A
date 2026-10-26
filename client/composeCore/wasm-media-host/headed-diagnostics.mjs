/* This is a private project. All rights reserved. */
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { createInterface } from "node:readline";
import { writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { parseGraphicsTrace } from "./graphics-trace-analysis.mjs";

export async function createDesktopProbe(
  browser,
  sourceWidth,
  { sampling = true } = {},
) {
  const session = await browser.newBrowserCDPSession();
  const processes = (await session.send("SystemInfo.getProcessInfo"))
    .processInfo;
  const browserProcess = processes.find((p) => p.type === "browser").id;
  const child = spawn(
    "pwsh",
    [
      "-NoProfile",
      "-File",
      resolve(import.meta.dirname, "desktop-frame-probe.ps1"),
      "-BrowserProcess",
      String(browserProcess),
      "-SourceWidth",
      String(sourceWidth),
      ...(sampling ? [] : ["-NoSampling"]),
    ],
    { windowsHide: true, stdio: ["pipe", "pipe", "pipe"] },
  );
  const lines = [],
    waiting = [];
  let failure = "",
    exited = false,
    started = false;
  const input = createInterface({ input: child.stdout });
  input.on("line", (line) => {
    let value;
    try {
      value = JSON.parse(line);
    } catch {
      failure += line;
      return;
    }
    if (waiting.length) waiting.shift().resolve(value);
    else lines.push(value);
  });
  child.stderr.on("data", (value) => (failure += value));
  const exit = new Promise((resolve) =>
    child.on("exit", (code) => {
      exited = true;
      for (const call of waiting.splice(0))
        call.reject(new Error(`Desktop probe exited ${code}: ${failure}`));
      resolve(code);
    }),
  );
  const next = () =>
    lines.length
      ? Promise.resolve(lines.shift())
      : exited
        ? Promise.reject(new Error(failure))
        : new Promise((resolve, reject) => waiting.push({ resolve, reject }));
  const ready = await next();
  assert.equal(ready.ready, true);
  return {
    bounds: ready.bounds,
    sampling,
    async start() {
      child.stdin.write("start\n");
      assert.equal((await next()).started, true);
      started = true;
    },
    async stop() {
      assert.ok(started);
      child.stdin.write("stop\n");
      const result = await next();
      started = false;
      child.stdin.end();
      assert.equal(await exit, 0, failure);
      assert.equal(result.Error, "");
      await session.detach();
      return result;
    },
    async close() {
      if (!exited) {
        child.stdin.end();
        await exit;
      }
      await session.detach().catch(() => {});
    },
  };
}

export async function startGraphicsTrace(browser) {
  const session = await browser.newBrowserCDPSession();
  const categories = [
    "cc",
    "viz",
    "gpu",
    "benchmark",
    "blink.user_timing",
    "devtools.timeline",
    "disabled-by-default-devtools.timeline.frame",
  ];
  await session.send("Tracing.start", {
    traceConfig: {
      recordMode: "recordContinuously",
      includedCategories: categories,
      excludedCategories: ["*"],
    },
    transferMode: "ReturnAsStream",
    streamFormat: "json",
    streamCompression: "gzip",
  });
  return {
    categories,
    async stop(path, { validate = true } = {}) {
      const complete = new Promise((resolve) =>
        session.once("Tracing.tracingComplete", resolve),
      );
      await session.send("Tracing.end");
      const result = await complete;
      assert.equal(result.dataLossOccurred, false);
      const chunks = [];
      try {
        for (;;) {
          const chunk = await session.send("IO.read", {
            handle: result.stream,
          });
          chunks.push(
            Buffer.from(chunk.data, chunk.base64Encoded ? "base64" : "utf8"),
          );
          if (chunk.eof) break;
        }
      } finally {
        await session.send("IO.close", { handle: result.stream });
        await session.detach();
      }
      const bytes = Buffer.concat(chunks);
      await writeFile(path, bytes);
      if (validate) {
        const events = parseGraphicsTrace(bytes).traceEvents;
        assert.ok(
          events.some((e) => e.name === "media.sample.start") &&
            events.some((e) => e.name === "media.sample.end"),
          "Measured trace window is missing",
        );
        assert.ok(
          events.some((e) => e.name === "PipelineReporter"),
          "Compositor feedback is missing",
        );
        assert.ok(
          events.some((e) => (e.cat ?? "").split(",").includes("gpu")),
          "GPU events are missing",
        );
      }
      return { path, categories };
    },
  };
}
