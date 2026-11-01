/* This is a private project. All rights reserved. */
import test from "node:test";
import { spawn } from "node:child_process";
import { stopNativeService } from "./verification-native-service.mjs";
import { AsyncLocalStorage } from "node:async_hooks";
import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
import { getReportDirectory } from "./verification-reports.mjs";

const scope = new AsyncLocalStorage();
let sequence = 0;
const defaults = { diagnosticTimeoutMs: 10000, cleanupTimeoutMs: 5000 };

function describe(error) {
  return error instanceof Error ? error.stack : String(error);
}

async function bounded(operation, milliseconds, label) {
  if (milliseconds <= 0)
    throw new Error(`${label} exceeded its diagnostic budget`);
  let timer;
  try {
    return await Promise.race([
      Promise.resolve().then(operation),
      new Promise((_, reject) => {
        timer = setTimeout(
          () => reject(new Error(`${label} timed out after ${milliseconds}ms`)),
          milliseconds,
        );
      }),
    ]);
  } finally {
    clearTimeout(timer);
  }
}

async function collect(owner, label, operation, deadline) {
  try {
    await bounded(operation, deadline - performance.now(), label);
  } catch (error) {
    owner.context.diagnostic(`${label}: ${describe(error)}`);
  }
}

async function cleanup(owner, label, operation, force) {
  try {
    await bounded(operation, owner.budgets.cleanupTimeoutMs, label);
  } catch (error) {
    owner.cleanupErrors.push(new Error(label, { cause: error }));
    if (force) {
      try {
        await bounded(force, owner.budgets.cleanupTimeoutMs, `Force ${label}`);
      } catch (failure) {
        owner.cleanupErrors.push(
          new Error(`Force ${label}`, { cause: failure }),
        );
      }
    }
  }
}

// Cleanup failures are deferred until the case's original outcome is known.
// Memoizing the operation also makes explicit finally blocks and fixture teardown
// share the same close attempt without replaying a failed native operation.
function ownCleanup(owner, label, operation, force) {
  let pending;
  const finish = () => (pending ??= cleanup(owner, label, operation, force));
  owner.cleanups.push(finish);
  return finish;
}

async function collectDriverFailure(owner) {
  for (const [name, read] of [
    [
      "page.png",
      async () => Buffer.from(await owner.driver.takeScreenshot(), "base64"),
    ],
    ["page.xml", () => owner.driver.getPageSource()],
    [
      "media.json",
      async () =>
        JSON.stringify(
          await owner.driver.executeScript(`
      return { states: window.states ?? null,
        host: window.host ? {status: host.status, metrics: host.metrics, error: host.errorMessage} : null,
        decoded: window.mediaTest?.decoded?.length ?? null,
        errors: window.mediaTest?.errors ?? [],
        contexts: window.mediaTest?.contexts?.map(c => c.state) ?? [],
        workers: window.mediaTest?.workers?.map(w => ({closed: w.closed})) ?? [],
        audio: window.mediaTest?.audio?.length ?? null };
    `),
          null,
          2,
        ),
    ],
  ]) {
    const deadline = performance.now() + owner.budgets.diagnosticTimeoutMs;
    await collect(
      owner,
      `Cannot collect ${name}`,
      async () => {
        const data = await bounded(read, deadline - performance.now(), name);
        await writeFile(resolve(owner.directory, name), data, {
          signal: AbortSignal.timeout(
            Math.max(1, Math.ceil(deadline - performance.now())),
          ),
        });
      },
      deadline,
    );
  }
}

export async function runCase(name, body, budgets = {}) {
  const parent = scope.getStore();
  const register = parent ? parent.context.test.bind(parent.context) : test;
  await register(name, async (context) => {
    const directory = resolve(
      await getReportDirectory(),
      "artifacts",
      `${process.pid}-${++sequence}-${name.replace(/[^a-z0-9-]/gi, "-")}`,
    );
    await mkdir(directory, { recursive: true });
    const state = {
      context,
      directory,
      driver: parent?.driver,
      cleanups: [],
      cleanupErrors: [],
      budgets: { ...(parent?.budgets ?? defaults), ...budgets },
    };
    await scope.run(state, async () => {
      let failure;
      try {
        await body();
      } catch (error) {
        failure = { error };
        if (state.driver) await collectDriverFailure(state);
      } finally {
        for (const finish of state.cleanups.toReversed()) await finish();
      }
      if (state.cleanupErrors.length) {
        for (const error of state.cleanupErrors)
          context.diagnostic(`${error.message}: ${describe(error.cause)}`);
        await collect(
          state,
          "Cannot save cleanup errors",
          () =>
            writeFile(
              resolve(directory, "cleanup-errors.json"),
              JSON.stringify(
                state.cleanupErrors.map((error) => ({
                  operation: error.message,
                  error: describe(error.cause),
                })),
                null,
                2,
              ),
              {
                signal: AbortSignal.timeout(state.budgets.diagnosticTimeoutMs),
              },
            ),
          performance.now() + state.budgets.diagnosticTimeoutMs,
        );
        failure ??= {
          error: new AggregateError(
            state.cleanupErrors,
            "Fixture cleanup failed",
          ),
        };
      }
      if (failure) {
        context.diagnostic(`Artifacts: ${directory}`);
        await collect(
          state,
          "Cannot save failure",
          () =>
            writeFile(
              resolve(directory, "failure.txt"),
              describe(failure.error),
              {
                signal: AbortSignal.timeout(state.budgets.diagnosticTimeoutMs),
              },
            ),
          performance.now() + state.budgets.diagnosticTimeoutMs,
        );
        throw failure.error;
      }
    });
  });
}

export function registerDriver(driver, forceClose) {
  const owner = scope.getStore();
  const quit = driver.quit.bind(driver);
  driver.quit = ownCleanup(owner, "Quit WebDriver", quit, forceClose);
  owner.driver = driver;
}

export function ownSite(site) {
  const owner = scope.getStore();
  site.close = ownCleanup(
    owner,
    "Close verification site",
    site.close.bind(site),
  );
  return site;
}

export async function launchBrowser(
  engine,
  options,
  { timingProbe = false } = {},
) {
  const owner = scope.getStore();
  const server = await engine.launchServer(options);
  const closeServer = ownCleanup(
    owner,
    "Close browser",
    () => server.close(),
    () => server.kill(),
  );
  // Register server ownership before opening the client connection.
  const browser = await engine.connect(server.wsEndpoint());
  const pendingPages = new Set();
  let closing;
  browser.close = () =>
    (closing ??= (async () => {
      for (const finish of [...pendingPages]) await finish();
      await closeServer();
    })());
  const newPage = browser.newPage.bind(browser);
  browser.newPage = async (options) => {
    const pageOwner = scope.getStore();
    const page = await newPage(options);
    const context = page.context();
    const errors = [];
    page.on("pageerror", (error) => errors.push(describe(error)));
    const close = page.close.bind(page);
    let closingPage;
    const finish = () =>
      (closingPage ??= (async () => {
        const prefix = resolve(pageOwner.directory, `page-${++sequence}`);
        const deadline =
          performance.now() + pageOwner.budgets.diagnosticTimeoutMs;
        try {
          await collect(
            pageOwner,
            "Media diagnostic collection failed",
            async () => {
              const media = await bounded(
                () =>
                  page.evaluate(() => ({
                    states: window.states,
                    host: window.host
                      ? {
                          status: host.status,
                          metrics: host.metrics,
                          error: host.errorMessage,
                        }
                      : null,
                    decoded: window.mediaTest?.decoded?.length,
                    errors: window.mediaTest?.errors ?? [],
                    contexts:
                      window.mediaTest?.contexts?.map((c) => c.state) ?? [],
                    workers:
                      window.mediaTest?.workers?.map((w) => ({
                        closed: w.closed,
                      })) ?? [],
                    audio: window.mediaTest?.audio?.length,
                  })),
                deadline - performance.now(),
                "Read media state",
              );
              await writeFile(
                `${prefix}.json`,
                JSON.stringify({ errors, media }, null, 2),
                {
                  signal: AbortSignal.timeout(
                    Math.max(1, Math.ceil(deadline - performance.now())),
                  ),
                },
              );
            },
            deadline,
          );
          const screenshotDeadline =
            performance.now() + pageOwner.budgets.diagnosticTimeoutMs;
          await collect(
            pageOwner,
            "Screenshot collection failed",
            () =>
              page.screenshot({
                path: `${prefix}.png`,
                timeout: Math.max(
                  1,
                  Math.ceil(screenshotDeadline - performance.now()),
                ),
              }),
            screenshotDeadline,
          );
          await collect(
            pageOwner,
            "Trace collection failed",
            () => context.tracing.stop({ path: `${prefix}-trace.zip` }),
            performance.now() + pageOwner.budgets.diagnosticTimeoutMs,
          );
        } finally {
          await cleanup(pageOwner, "Close page", close);
          pendingPages.delete(finish);
        }
      })());
    page.close = finish;
    pendingPages.add(finish);
    pageOwner.cleanups.push(finish);
    try {
      await bounded(
        () =>
          context.tracing.start({
            // Timing probes retain action traces and a final PNG without a second
            // periodic capture stream competing with canvas frame measurements.
            screenshots: !timingProbe,
            snapshots: !timingProbe,
            sources: true,
          }),
        pageOwner.budgets.diagnosticTimeoutMs,
        "Start trace",
      );
    } catch (error) {
      pageOwner.context.diagnostic(
        `Trace initialization failed: ${describe(error)}`,
      );
    }
    return page;
  };
  return browser;
}

// Geckodriver is an owned child, not a service hidden behind Builder. Its entire
// process tree remains available for forced teardown if WebDriver quit stalls.
export async function launchFirefoxDriver(
  selenium,
  { executable, profileRoot, options },
) {
  const owner = scope.getStore();
  const service = spawn(
    executable,
    [
      "--host",
      "127.0.0.1",
      "--port",
      "0",
      "--websocket-port",
      "0",
      "--profile-root",
      profileRoot,
    ],
    {
      windowsHide: true,
      detached: process.platform !== "win32",
      stdio: ["ignore", "pipe", "pipe"],
    },
  );
  const output = [];
  let address;
  const ready = new Promise((resolve, reject) => {
    service.once("error", reject);
    service.once("exit", (code, signal) =>
      reject(
        new Error(`Geckodriver exited before session setup: ${code ?? signal}`),
      ),
    );
    for (const stream of [service.stdout, service.stderr])
      stream.on("data", (data) => {
        output.push(data);
        const match = Buffer.concat(output)
          .toString()
          .match(/Listening on (127\.0\.0\.1:\d+)/);
        if (match && !address) {
          address = `http://${match[1]}`;
          resolve(address);
        }
      });
  });
  const stop = ownCleanup(owner, "Stop geckodriver process", async () => {
    await stopNativeService(service, owner.budgets.cleanupTimeoutMs);
    await writeFile(
      resolve(owner.directory, "geckodriver.log"),
      Buffer.concat(output),
      { signal: AbortSignal.timeout(owner.budgets.cleanupTimeoutMs) },
    );
  });
  const url = await bounded(() => ready, 30000, "Start geckodriver");
  const driver = await new selenium.Builder()
    .forBrowser("firefox")
    .usingServer(url)
    .setFirefoxOptions(options)
    .build();
  registerDriver(driver, stop);
  return driver;
}
