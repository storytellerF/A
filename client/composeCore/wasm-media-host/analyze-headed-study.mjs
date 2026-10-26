/* This is a private project. All rights reserved. */
import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";
import { resolve, relative, isAbsolute } from "node:path";
import { work } from "./verification-site.mjs";
import {
  parseGraphicsTrace,
  analyzeGraphicsTrace,
} from "./graphics-trace-analysis.mjs";

function artifact(name) {
  const path = resolve(work, name),
    part = relative(work, path);
  assert.ok(
    part !== ".." &&
      !part.startsWith("../") &&
      !part.startsWith("..\\") &&
      !isAbsolute(part),
  );
  return path;
}
const source = JSON.parse(
  await readFile(artifact(process.argv[2] ?? "headed-study.json"), "utf8"),
);
const rows = [];
for (const sample of source.results) {
  const analysis = sample.graphicsTrace
    ? analyzeGraphicsTrace(
        parseGraphicsTrace(await readFile(artifact(sample.graphicsTrace.path))),
        sample,
      )
    : null;
  rows.push({
    browser: sample.browser,
    version: sample.version,
    variant: sample.variant,
    round: sample.round,
    capture: sample.capture,
    capturePolicy: sample.capturePolicy ?? null,
    captureRequestMode: sample.captureRequestMode ?? null,
    headed: sample.headed,
    sourceFps: sample.sourceFps,
    sourceHeight: sample.height,
    elapsed: sample.elapsed,
    importFps: sample.importFps,
    // Old datasets used a generic observation label for capture-only values.
    captureFps:
      sample.canvasCaptureUniqueFps ?? sample.observedUniqueFps ?? null,
    desktopFps:
      sample.desktopObservedUniqueFps ?? sample.desktop?.uniqueFps ?? null,
    desktopSampling: sample.desktop?.sampling ?? false,
    physicalBounds: sample.desktop?.bounds ?? null,
    mainTaskMsPerSecond: sample.mainTaskMs / sample.elapsed,
    analysis,
  });
}
await writeFile(
  artifact(process.argv[3] ?? "headed-analysis.json"),
  JSON.stringify(
    {
      measurementSchema: source.measurementSchema ?? null,
      samples: rows.length,
      rows,
    },
    null,
    2,
  ),
);
console.info(
  JSON.stringify(
    rows.map((r) => ({
      browser: r.browser,
      variant: r.variant,
      round: r.round,
      capture: r.capture,
      desktop: r.desktopFps,
      stream: r.captureFps,
      glVideo: r.analysis?.videoDraws,
      confirmedCaptureLoss: r.analysis?.confirmedCaptureLoss.length,
      notSeenOnDesktop: r.analysis?.unobservedOnDesktop.length,
      states: r.analysis?.reporterCounts,
    })),
    null,
    2,
  ),
);
