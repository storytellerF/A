/* This is a private project. All rights reserved. */
import test from "node:test";
import assert from "node:assert/strict";
import { MediaHost } from "./host.mjs";

function fixture(t) {
  let now = 1;
  const tasks = new Map();
  let next = 0;
  const frames = [];
  const stamp = { contextTime: 0.8, performanceTime: 1000 };
  const context = {
    currentTime: 1,
    state: "running",
    getOutputTimestamp: () => stamp,
    async close() { this.state = "closed"; },
  };
  const host = new MediaHost("https://example.invalid/media", () => {},
    (frame) => frames.push(frame), {
      autoStart: false,
      now: () => now,
      scheduler: {
        setTimeout(callback) { tasks.set(++next, callback); return next; },
        clearTimeout(id) { tasks.delete(id); },
      },
    });
  Object.assign(host, {
    context, status: "playing", intent: "playing", duration: 10,
    clockStart: 0, audioEnded: true,
  });
  t.after(() => host.close());
  return { host, context, stamp, frames, advance: (value) => { now = value; } };
}

const near = (actual, expected) => assert.ok(Math.abs(actual - expected) < 1e-9,
  `${actual} != ${expected}`);
const frame = (timestamp) => ({ timestamp, duration: 0.03, pixels: new ArrayBuffer(4) });

test("output timestamps map performance seconds to the audible media position", (t) => {
  const { host, context, advance } = fixture(t);
  host.position = 3;
  host.clockStart = 0.5;
  context.currentTime = 1.2;
  advance(1.08);
  near(host.renderTime(), 3.7);
  near(host.currentTime(), 3.38);
});

test("video delivery waits for the output clock instead of the render queue", (t) => {
  const { host, context, frames, advance } = fixture(t);
  context.currentTime = 1.2;
  host.frame = frame(0.85);
  host.tick(host.generation);
  assert.equal(frames.length, 0);
  advance(1.06);
  host.tick(host.generation);
  assert.equal(frames.length, 1);
  assert.equal(host.metrics.dropped, 0);
});

test("render-ahead audio does not make an on-time output frame look late", (t) => {
  const { host, context, stamp, frames } = fixture(t);
  context.currentTime = 1.3;
  stamp.contextTime = 0.92;
  host.frame = frame(0.9);
  host.tick(host.generation);
  assert.equal(frames.length, 1);
  assert.equal(host.metrics.dropped, 0);
  near(host.metrics.maxSkew, 0.02);
});

test("audio offsets still trim against rendered sound rather than delayed output", async (t) => {
  const { host, context } = fixture(t);
  context.currentTime = 1.1;
  const starts = [];
  context.createBuffer = () => ({ copyToChannel() {} });
  context.createBufferSource = () => ({
    connect() {}, disconnect() {}, stop() {},
    start(...args) { starts.push(args); },
  });
  host.gain = { disconnect() {} };
  host.audioEnded = false;
  host.audioEnd = 0;
  host.firstAudio = {
    channels: [new Float32Array(9600)], sampleRate: 48000,
    timestamp: 0.95, duration: 0.2,
  };
  host.rpc = async () => null;
  await host.pumpAudio(host.generation);
  assert.equal(starts.length, 1);
  near(starts[0][0], 1.1);
  near(starts[0][1], 0.15);
  assert.equal(host.audioEnded, true);
});

test("buffer starvation is detected at the render queue even while output trails it", (t) => {
  const { host, context } = fixture(t);
  let suspends = 0;
  context.suspend = async () => { suspends++; context.state = "suspended"; };
  host.audioEnded = false;
  host.audioEnd = 1.02;
  host.tick(host.generation);
  assert.equal(host.status, "buffering");
  assert.equal(suspends, 1);
});

test("buffering drains queued output and then freezes at the suspended render clock", (t) => {
  const { host, context, frames, advance } = fixture(t);
  host.status = "buffering";
  context.state = "suspended";
  host.frame = frame(0.85);
  host.tick(host.generation);
  assert.equal(frames.length, 0);
  advance(1.1);
  host.tick(host.generation);
  assert.equal(frames.length, 1);
  advance(2);
  near(host.currentTime(), 1);
  advance(3);
  near(host.currentTime(), 1);
});

test("playback ends after the last audio reaches output rather than render completion", (t) => {
  const { host, advance } = fixture(t);
  host.duration = 1;
  host.videoEnded = true;
  host.tick(host.generation);
  assert.equal(host.status, "playing");
  advance(1.3);
  host.tick(host.generation);
  assert.equal(host.status, "ended");
  assert.equal(host.position, 1);
});

test("timestamp refinements hold video without rewinding its position", (t) => {
  const { host, stamp } = fixture(t);
  near(host.currentTime(), 0.8);
  stamp.contextTime = 0.7;
  near(host.currentTime(), 0.8);
  stamp.contextTime = 0.9;
  near(host.currentTime(), 0.9);
});

test("a future performance timestamp projects backward to the current output position", (t) => {
  const { host, context, stamp } = fixture(t);
  context.currentTime = 2;
  stamp.contextTime = 1.5;
  stamp.performanceTime = 2000;
  near(host.currentTime(), 0.5);
});

for (const timestamp of [undefined, { contextTime: 0, performanceTime: 0 },
  { contextTime: NaN, performanceTime: 1000 }]) {
  test(`latency estimates cover unavailable output timestamps: ${JSON.stringify(timestamp)}`, (t) => {
    const { host, context } = fixture(t);
    context.getOutputTimestamp = timestamp ? () => timestamp : undefined;
    context.baseLatency = 0.02;
    context.outputLatency = 0.18;
    near(host.currentTime(), 0.8);
    context.outputLatency = 0.3;
    near(host.currentTime(), 0.8);
  });
}

test("output progress stays at the seek position until the scheduled start reaches output", (t) => {
  const { host, stamp } = fixture(t);
  host.position = 4;
  host.clockStart = 0.9;
  near(host.currentTime(), 4);
  stamp.contextTime = 0.95;
  near(host.currentTime(), 4.05);
});

test("seek starts a fresh output clock instead of retaining the previous position", async (t) => {
  const { host } = fixture(t);
  host.status = "paused";
  host.intent = "paused";
  host.outputPosition = 9;
  host.createWorker = () => ({
    postMessage({ id }) {
      queueMicrotask(() => host.worker.onmessage({ data: {
        id, result: { duration: 10, audio: false, video: false },
      } }));
    },
    terminate() {},
  });
  await host.seek(2);
  near(host.outputPosition, 2);
  near(host.currentTime(), 2);
});

test("silent video retains the monotonic performance clock", (t) => {
  const { host, advance } = fixture(t);
  host.context = null;
  advance(1.5);
  near(host.currentTime(), 1.5);
});

test("pause saves the output position instead of skipping queued audio on resume", async (t) => {
  const { host } = fixture(t);
  let pausedAt;
  host.open = async (time) => { pausedAt = time; };
  host.pause();
  await host.ready;
  near(pausedAt, 0.8);
  assert.equal(host.intent, "paused");
});
