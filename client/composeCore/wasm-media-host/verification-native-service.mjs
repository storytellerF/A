/* This is a private project. All rights reserved. */
import { spawn } from "node:child_process";

function waitFor(operation, milliseconds, label) {
  let timer;
  return Promise.race([
    operation,
    new Promise((_, reject) => {
      timer = setTimeout(
        () => reject(new Error(`${label} timed out`)),
        milliseconds,
      );
    }),
  ]).finally(() => clearTimeout(timer));
}

// Best-effort fixture shutdown of a live owned service. This is not the release
// boundary: the outer Job/subreaper still owns descendants after this root exits.
export async function stopNativeService(child, timeoutMs = 5000) {
  if (child.exitCode !== null || child.signalCode !== null) return;
  if (
    !Number.isInteger(child.pid) ||
    child.pid <= 0 ||
    child.pid === process.pid
  )
    throw new Error("Cannot terminate an unowned process");
  const exited = new Promise((resolve) => child.once("close", resolve));
  if (process.platform === "win32") {
    await waitFor(
      new Promise((resolve, reject) => {
        const killer = spawn(
          "taskkill.exe",
          ["/PID", String(child.pid), "/T", "/F"],
          { windowsHide: true, stdio: "ignore" },
        );
        killer.once("error", reject);
        killer.once("close", (code) => {
          if (
            code === 0 ||
            child.exitCode !== null ||
            child.signalCode !== null
          )
            resolve();
          else
            reject(
              new Error(
                `Cannot terminate process tree: taskkill exited ${code}`,
              ),
            );
        });
      }),
      timeoutMs,
      "Terminate process tree",
    );
  } else {
    try {
      process.kill(-child.pid, "SIGKILL");
    } catch (error) {
      if (error.code !== "ESRCH") throw error;
    }
  }
  await waitFor(exited, timeoutMs, "Wait for process tree exit");
}
