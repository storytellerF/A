/* This is a private project. All rights reserved. */
import test from "node:test";
import assert from "node:assert/strict";
import {
  classifyUnuploadedFrame,
  parseGraphicsTrace,
  traceIntervals,
  analyzeGraphicsTrace,
} from "./graphics-trace-analysis.mjs";

function fixture() {
  const traceEvents = [
    { name: "media.sample.start", pid: 1, ts: 0, ph: "I" },
    { name: "media.sample.end", pid: 1, ts: 1000, ph: "I" },
  ];
  for (let id = 1; id <= 8; id++) {
    const base = {
      pid: 1,
      tid: 2,
      id2: { local: "page" },
      name: "AnimationFrame",
    };
    traceEvents.push(
      {
        ...base,
        ph: "b",
        ts: id * 10,
        args: {
          id: `animation-${id}`,
          animation_frame_timing_info: {
            begin_frame_id: { source_id: 3, sequence_number: id },
          },
        },
      },
      { ...base, ph: "e", ts: id * 10 + 8, args: {} },
      {
        pid: 1,
        tid: 2,
        name: `media.video.draw:${id / 30}`,
        ph: "I",
        ts: id * 10 + 4,
      },
      {
        pid: 1,
        name: "AnimationFrame::Presentation",
        ph: "n",
        ts: id * 10 + 9,
        args: { id: `animation-${id}` },
      },
      {
        pid: 1,
        name: "PipelineReporter",
        ph: "X",
        ts: id * 10,
        dur: 9,
        args: {
          frame_reporter: {
            frame_source: 3,
            frame_sequence: id,
            layer_tree_host_id: 2,
            state: "STATE_PRESENTED_ALL",
          },
        },
      },
    );
  }
  return {
    trace: { traceEvents },
    sample: {
      sourceFps: 30,
      capture: true,
      frames: [1, 2, 3, 5, 7, 8].map((id) => ({ id })),
      desktop: {
        sampling: true,
        frames: [1, 2, 3, 4, 5, 6, 7, 8].map((Id) => ({ Id })),
        readMs: [1, 2],
      },
    },
  };
}
test("large numeric trace identifiers retain exact source digits", () => {
  const parsed = parseGraphicsTrace(
    '{"a":9007199254740993,"b":9007199254740992,"small":42}',
  );
  assert.equal(parsed.a, "9007199254740993");
  assert.equal(parsed.b, "9007199254740992");
  assert.equal(parsed.small, 42);
});
test("reused animation track identifiers produce separate completed intervals", () => {
  const { trace } = fixture();
  const intervals = traceIntervals(trace.traceEvents, "AnimationFrame");
  assert.equal(intervals.length, 8);
  assert.equal(intervals[0].args.id, "animation-1");
  assert.equal(intervals.at(-1).args.id, "animation-8");
});
test("capture loss requires interior desktop evidence and matching presentation feedback", () => {
  const { trace, sample } = fixture();
  const result = analyzeGraphicsTrace(trace, sample);
  assert.equal(result.videoFeedback, 8);
  assert.deepEqual(result.confirmedCaptureLoss, [4, 6]);
  assert.deepEqual(result.unobservedOnDesktop, []);
  assert.deepEqual(result.interiorIdRange, [1, 8]);
});
test("shared presentation timestamps remain coalescing ambiguity rather than independent presented frames", () => {
  const { trace, sample } = fixture();
  trace.traceEvents.find(
    (e) =>
      e.name === "AnimationFrame::Presentation" && e.args.id === "animation-4",
  ).ts = 59;
  const result = analyzeGraphicsTrace(trace, sample);
  assert.equal(result.videoFeedback, 8);
  assert.equal(result.uniqueVideoFeedbackTimes, 7);
  assert.deepEqual(result.coalesced, [{ presentationUs: 59, ids: [4, 5] }]);
  assert.deepEqual(result.confirmedCaptureLoss, [6]);
});
test("no-update reporters and other processes are not counted as dropped target frames", () => {
  const { trace, sample } = fixture();
  trace.traceEvents.find(
    (e) => e.name === "PipelineReporter",
  ).args.frame_reporter.state = "STATE_NO_UPDATE_DESIRED";
  trace.traceEvents.push({
    pid: 99,
    name: "PipelineReporter",
    ph: "X",
    ts: 10,
    dur: 9,
    args: {
      frame_reporter: {
        state: "STATE_DROPPED",
        frame_source: 3,
        frame_sequence: 1,
      },
    },
  });
  const result = analyzeGraphicsTrace(trace, sample);
  assert.equal(result.reporterCounts.STATE_NO_UPDATE_DESIRED, 1);
  assert.equal(result.reporterCounts.STATE_PRESENTED_ALL, 7);
  assert.equal(result.reporterCounts.STATE_DROPPED, undefined);
});
test("missing feedback remains unknown and does not become a confirmed capture or presentation loss", () => {
  const { trace, sample } = fixture();
  trace.traceEvents = trace.traceEvents.filter(
    (e) =>
      !(
        e.name === "AnimationFrame::Presentation" && e.args.id === "animation-4"
      ),
  );
  sample.desktop.frames = sample.desktop.frames.filter((e) => e.Id !== 4);
  const result = analyzeGraphicsTrace(trace, sample);
  assert.equal(result.videoFeedback, 7);
  assert.deepEqual(result.confirmedCaptureLoss, [6]);
  assert.equal(result.unobservedOnDesktop[0].id, 4);
  assert.equal(result.unobservedOnDesktop[0].presentationUs, null);
});
test("incomplete measurement windows are rejected", () => {
  const { trace, sample } = fixture();
  trace.traceEvents = trace.traceEvents.filter(
    (e) => e.name !== "media.sample.end",
  );
  assert.throws(
    () => analyzeGraphicsTrace(trace, sample),
    /measured end marker/,
  );
});

test("desktop-only evidence remains available when RGBA has no tagged video draw", () => {
  const { trace, sample } = fixture();
  trace.traceEvents = trace.traceEvents.filter(
    (e) => !e.name.startsWith("media.video.draw:"),
  );
  const result = analyzeGraphicsTrace(trace, sample);
  assert.equal(result.videoFeedback, 0);
  assert.deepEqual(result.desktopSeenNotCaptured, [4, 6]);
  assert.deepEqual(result.confirmedCaptureLoss, []);
});

test("unknown compositor states cannot silently become presentation conclusions", () => {
  const { trace, sample } = fixture();
  trace.traceEvents.find(
    (e) => e.name === "PipelineReporter",
  ).args.frame_reporter.state = "FUTURE_STATE";
  assert.throws(
    () => analyzeGraphicsTrace(trace, sample),
    /Unknown frame reporter state/,
  );
});

test("pending replacement is distinguished from its outer Host tick caller", () => {
  assert.equal(
    classifyUnuploadedFrame({
      stack:
        "at GpuBridge.releasePending\nat GpuBridge.replace\nat Host.present\nat Host.tick",
    }),
    "pending-replacement",
  );
  assert.equal(
    classifyUnuploadedFrame({ stack: "at Host.tick" }),
    "host-tick-close",
  );
});

test("cleanup without replacement is not labelled as a pending replacement", () => {
  assert.equal(
    classifyUnuploadedFrame({
      stack: "at GpuBridge.releasePending\nat GpuBridge.close",
    }),
    "unknown",
  );
});
