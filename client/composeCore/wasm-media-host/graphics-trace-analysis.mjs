/* This is a private project. All rights reserved. */
import assert from "node:assert/strict";
import { gunzipSync } from "node:zlib";

export function classifyUnuploadedFrame(frame) {
  const stack = frame.stack ?? "";
  if (stack.includes(".releasePending") && stack.includes(".replace")) {
    return "pending-replacement";
  }
  return stack.includes(".tick") ? "host-tick-close" : "unknown";
}

export function parseGraphicsTrace(bytes) {
  const text = Buffer.isBuffer(bytes)
    ? gunzipSync(bytes).toString("utf8")
    : bytes;
  // Chrome exports 64-bit surface IDs as JSON numbers. Preserve their original
  // digits instead of silently joining different IDs after IEEE-754 rounding.
  return JSON.parse(text, (_key, value, context) =>
    typeof value === "number" &&
    Number.isInteger(value) &&
    !Number.isSafeInteger(value)
      ? context.source
      : value,
  );
}
function asyncKey(event) {
  const id = event.id2?.global ?? event.id2?.local ?? event.id;
  return `${event.name}/${event.id2?.global !== undefined ? "global" : event.pid}/${id}`;
}
export function traceIntervals(events, name) {
  const pending = new Map(),
    result = [];
  for (const event of events
    .filter((e) => e.name === name)
    .toSorted((a, b) => a.ts - b.ts)) {
    if (event.ph === "X") result.push({ ...event, end: event.ts + event.dur });
    else if (event.ph === "b") pending.set(asyncKey(event), event);
    else if (event.ph === "e") {
      const begin = pending.get(asyncKey(event));
      if (begin) {
        result.push({ ...begin, end: event.ts });
        pending.delete(asyncKey(event));
      }
    }
  }
  return result;
}
function reporterOf(event) {
  return event.args?.frame_reporter ?? event.args?.chrome_frame_reporter;
}
function frameKey(id) {
  return `${id.source_id}/${id.sequence_number}`;
}
const states = [
  "STATE_NO_UPDATE_DESIRED",
  "STATE_PRESENTED_ALL",
  "STATE_PRESENTED_PARTIAL",
  "STATE_DROPPED",
];
function stateOf(reporter) {
  const state =
    typeof reporter.state === "number"
      ? states[reporter.state]
      : reporter.state;
  assert.ok(
    states.includes(state),
    `Unknown frame reporter state: ${reporter.state}`,
  );
  return state;
}
const countBy = (items, key) =>
  items.reduce((counts, item) => {
    const k = key(item);
    counts[k] = (counts[k] ?? 0) + 1;
    return counts;
  }, {});
const quantile = (values, p) =>
  values.length
    ? values.toSorted((a, b) => a - b)[
        Math.min(values.length - 1, Math.floor(values.length * p))
      ]
    : null;

export function analyzeGraphicsTrace(trace, sample) {
  const events = trace.traceEvents;
  const starts = events.filter((e) => e.name === "media.sample.start"),
    ends = events.filter((e) => e.name === "media.sample.end");
  assert.equal(starts.length, 1, "Expected one measured start marker");
  assert.equal(ends.length, 1, "Expected one measured end marker");
  const start = starts[0],
    end = ends[0];
  assert.equal(start.pid, end.pid);
  assert.ok(end.ts > start.ts);
  const renderer = events.filter((e) => e.pid === start.pid);
  const reporters = traceIntervals(renderer, "PipelineReporter").filter(
    (e) => e.end >= start.ts && e.end < end.ts && reporterOf(e),
  );
  const byFrame = new Map();
  for (const event of reporters) {
    const r = reporterOf(event),
      key = `${r.frame_source}/${r.frame_sequence}`;
    if (!byFrame.has(key)) byFrame.set(key, []);
    byFrame.get(key).push(stateOf(r));
  }
  const animations = traceIntervals(renderer, "AnimationFrame");
  const feedback = new Map(
    renderer
      .filter((e) => e.name === "AnimationFrame::Presentation")
      .map((e) => [e.args.id, e]),
  );
  const draws = renderer.filter(
    (e) =>
      e.name.startsWith("media.video.draw:") &&
      e.ts >= start.ts &&
      e.ts < end.ts,
  );
  const videos = draws.map((draw) => {
    const frame = animations.find((e) => e.ts <= draw.ts && draw.ts <= e.end);
    const presentation = frame ? feedback.get(frame.args.id) : undefined;
    const begin = frame?.args.animation_frame_timing_info?.begin_frame_id;
    return {
      id: Math.round(Number(draw.name.split(":")[1]) * sample.sourceFps),
      drawUs: draw.ts,
      animationId: frame?.args.id ?? null,
      presentationUs: presentation?.ts ?? null,
      reporterStates: begin ? (byFrame.get(frameKey(begin)) ?? []) : [],
    };
  });
  const groups = new Map();
  for (const video of videos)
    if (video.presentationUs !== null) {
      const k = video.presentationUs;
      if (!groups.has(k)) groups.set(k, []);
      groups.get(k).push(video.id);
    }
  const coalesced = [...groups.entries()]
    .filter(([, ids]) => new Set(ids).size > 1)
    .map(([presentationUs, ids]) => ({ presentationUs, ids }));
  const desktop = new Set((sample.desktop?.frames ?? []).map((f) => f.Id));
  const captured = new Set((sample.frames ?? []).map((f) => f.id));
  const ids = videos.map((v) => v.id);
  const ranges = [
    ids,
    [...desktop],
    ...(sample.capture ? [[...captured]] : []),
  ].filter((a) => a.length);
  const low = ranges.length
    ? Math.max(...ranges.map((a) => Math.min(...a)))
    : 0;
  const high = ranges.length
    ? Math.min(...ranges.map((a) => Math.max(...a)))
    : 0;
  const interior = (id) => id > low && id < high;
  // A feedback callback alone cannot prove that every intermediate canvas value
  // was scanned out. Keep coalescing and unobserved IDs separate from drop claims.
  const confirmedCaptureLoss = sample.capture
    ? videos
        .filter(
          (v) =>
            interior(v.id) &&
            desktop.has(v.id) &&
            !captured.has(v.id) &&
            v.presentationUs !== null &&
            new Set(groups.get(v.presentationUs)).size === 1,
        )
        .map((v) => v.id)
    : [];
  const unobservedOnDesktop =
    sample.desktop?.sampling !== false
      ? videos
          .filter((v) => interior(v.id) && !desktop.has(v.id))
          .map((v) => ({
            id: v.id,
            reporterStates: v.reporterStates,
            presentationUs: v.presentationUs,
          }))
      : [];
  const inWindow = events.filter((e) => e.ts >= start.ts && e.ts < end.ts);
  const presentCalls = inWindow.filter(
    (e) => e.name === "DXGISwapChainImageBacking::Present" && e.ph === "X",
  );
  const submitLatency = traceIntervals(
    renderer,
    "SubmitCompositorFrameToPresentationCompositorFrame",
  )
    .filter((e) => e.end >= start.ts && e.end < end.ts)
    .map((e) => (e.end - e.ts) / 1000);
  return {
    rendererPid: start.pid,
    windowUs: [start.ts, end.ts],
    elapsed: (end.ts - start.ts) / 1e6,
    reporterCounts: countBy(reporters, (e) => stateOf(reporterOf(e))),
    reporterReasons: countBy(
      reporters.filter((e) => stateOf(reporterOf(e)) === "STATE_DROPPED"),
      (e) => reporterOf(e).reason ?? "unspecified",
    ),
    videoDraws: videos.length,
    videoFeedback: videos.filter((v) => v.presentationUs !== null).length,
    uniqueVideoFeedbackTimes: groups.size,
    coalesced,
    interiorIdRange: [low, high],
    desktopSeenNotCaptured: sample.capture
      ? [...desktop].filter((id) => interior(id) && !captured.has(id))
      : [],
    confirmedCaptureLoss: [...new Set(confirmedCaptureLoss)],
    unobservedOnDesktop,
    desktopReadMsP95: quantile(sample.desktop?.readMs ?? [], 0.95),
    driverPresentCalls: presentCalls.length,
    driverPresentCallMsP95: quantile(
      presentCalls.map((e) => e.dur / 1000),
      0.95,
    ),
    submitToFeedbackMsP50: quantile(submitLatency, 0.5),
    submitToFeedbackMsP95: quantile(submitLatency, 0.95),
    videos,
  };
}
