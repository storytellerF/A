/* This is a private project. All rights reserved. */
import test from "node:test";
import assert from "node:assert/strict";
import createWasmHost, { MediaHost } from "./host.mjs";

const flush = () => new Promise((resolve) => setImmediate(resolve));

function controlledScheduler() {
  const tasks = new Map();
  let next = 0;
  return {
    tasks,
    setTimeout(callback) {
      const id = ++next;
      tasks.set(id, callback);
      return id;
    },
    clearTimeout(id) {
      tasks.delete(id);
    },
  };
}
function ownedFrame() {
  let closes = 0;
  return {
    timestamp: 0,
    duration: 0.033,
    width: 1,
    height: 1,
    readbackMs: 0,
    frame: {
      close() {
        closes++;
      },
    },
    get closes() {
      return closes;
    },
  };
}
function tickFixture(onFrame) {
  const scheduler = controlledScheduler();
  let time = 0;
  const host = new MediaHost(
    "https://example.invalid/media",
    () => {},
    onFrame,
    { autoStart: false, scheduler, now: () => time },
  );
  Object.assign(host, {
    status: "playing",
    intent: "playing",
    duration: 10,
    clockStart: 0,
    audioEnded: true,
  });
  return {
    host,
    scheduler,
    advance(value) {
      time = value;
    },
  };
}

test("a busy consumer retains the Host frame until ownership can transfer", async () => {
  let ready = false;
  const accepted = [];
  const { host } = tickFixture((frame) => {
    if (!ready) return false;
    accepted.push(frame);
    return true;
  });
  const frame = ownedFrame();
  host.frame = frame;
  host.tick(host.generation);
  host.tick(host.generation);
  assert.equal(host.frame, frame);
  assert.equal(frame.closes, 0);
  assert.equal(host.metrics.acceptedFrames, 0);
  assert.equal(host.metrics.backpressureWaits, 2);
  assert.equal(host.metrics.dropped, 0);
  ready = true;
  host.tick(host.generation);
  assert.equal(host.frame, null);
  assert.equal(host.metrics.acceptedFrames, 1);
  assert.equal(accepted.length, 1);
  assert.equal(accepted[0].generation, host.generation);
  assert.ok(Object.isFrozen(accepted[0]));
  await host.close();
  assert.equal(frame.closes, 0);
  frame.frame.close();
});

test("backpressure preserves the existing lateness limit instead of accumulating stale frames", async () => {
  const { host, advance } = tickFixture(() => false);
  const frame = ownedFrame();
  host.frame = frame;
  host.tick(host.generation);
  advance(0.2);
  host.tick(host.generation);
  assert.equal(host.frame, null);
  assert.equal(frame.closes, 1);
  assert.equal(host.metrics.dropped, 1);
  assert.equal(host.metrics.acceptedFrames, 0);
  await host.close();
  assert.equal(frame.closes, 1);
});

test("a throwing consumer fails playback and releases the caller-owned frame once", async () => {
  const { host } = tickFixture(() => {
    throw new Error("consumer failed");
  });
  const frame = ownedFrame();
  host.frame = frame;
  host.tick(host.generation);
  assert.equal(host.status, "error");
  assert.equal(frame.closes, 1);
  assert.equal(host.frame, null);
  await host.close();
  assert.equal(frame.closes, 1);
});

test("closing a blocked preview cancels its wait and closes the untransferred frame once", async () => {
  const scheduler = controlledScheduler(),
    frame = ownedFrame();
  const host = new MediaHost(
    "https://example.invalid/media",
    () => {},
    () => false,
    { autoStart: false, scheduler },
  );
  host.ready = host.presentPreview(frame, host.generation);
  assert.equal(host.sleepers.size, 1);
  await host.close();
  assert.equal(frame.closes, 1);
  assert.equal(host.sleepers.size, 0);
  assert.equal(scheduler.tasks.size, 0);
});

test("consumption wakes delivery while late acknowledgements cannot revive a cancelled generation", async () => {
  const scheduler = controlledScheduler(),
    packets = [];
  const host = new MediaHost(
    "https://example.invalid/media",
    () => {},
    (f) => {
      packets.push(f);
      return true;
    },
    { autoStart: false, path: "gpu-texture", scheduler },
  );
  Object.assign(host, {
    status: "playing",
    intent: "playing",
    duration: 10,
    clockStart: 0,
    audioEnded: true,
    now: () => 0,
  });
  const frame = ownedFrame();
  host.frame = frame;
  host.tick(host.generation);
  packets[0].onConsumed();
  assert.equal(scheduler.tasks.size, 1);
  await host.close();
  assert.equal(scheduler.tasks.size, 0);
  packets[0].onConsumed();
  assert.equal(scheduler.tasks.size, 0);
  frame.frame.close();
});

test("an eligible frame blocked by rendering prevents additional decoder requests", async () => {
  const scheduler = controlledScheduler(),
    produced = [];
  let ready = true;
  const { host, workers, frames } = fixture({
    path: "gpu-texture",
    scheduler,
    makeFrame: () => {
      const f = ownedFrame();
      produced.push(f);
      return f;
    },
    acceptFrame: () => ready,
  });
  await host.ready;
  ready = false;
  await host.play();
  await flush();
  host.tick(host.generation);
  await flush();
  assert.equal(workers[0].messages.filter((m) => m.op === "video").length, 2);
  assert.equal(host.frame, produced[1]);
  assert.equal(produced[1].closes, 0);
  assert.equal(frames.length, 1);
  await host.close();
  assert.equal(produced[1].closes, 1);
  frames[0].frame.close();
});
function fixture({
  audio = false,
  deferOpen = false,
  createAudio,
  path,
  makeFrame,
  acceptFrame,
  scheduler,
} = {}) {
  const workers = [],
    states = [],
    frames = [];
  class Worker {
    constructor() {
      this.messages = [];
      this.terminated = false;
      workers.push(this);
    }
    postMessage(message) {
      this.messages.push(message);
      if (message.op === "open" && !deferOpen)
        queueMicrotask(() =>
          this.reply(message, { duration: 10, video: true, audio }),
        );
      if (message.op === "video")
        queueMicrotask(() =>
          this.reply(
            message,
            makeFrame
              ? makeFrame()
              : {
                  pixels: new ArrayBuffer(4),
                  width: 1,
                  height: 1,
                  timestamp: 5,
                  duration: 1,
                  readbackMs: 0,
                },
          ),
        );
      if (message.op === "audio")
        queueMicrotask(() => this.reply(message, null));
    }
    reply(message, result) {
      this.onmessage({ data: { id: message.id, result } });
    }
    terminate() {
      this.terminated = true;
    }
  }
  const host = new MediaHost(
    "https://example.invalid/media",
    (s) => states.push(s),
    (f) => {
      if (acceptFrame?.(f) === false) return false;
      frames.push(f);
      return true;
    },
    {
      path,
      scheduler,
      createWorker: () => new Worker(),
      createAudio,
      now: () => 0,
    },
  );
  return { host, workers, states, frames };
}

test("closing during metadata read cancels generation and suppresses late publication", async () => {
  const { host, workers, states } = fixture({ deferOpen: true });
  await host.close();
  const count = states.length;
  workers[0].reply(workers[0].messages[0], {
    duration: 10,
    video: false,
    audio: false,
  });
  await host.ready;
  assert.equal(states.length, count);
  assert.equal(host.status, "closed");
  assert.equal(host.pending.size, 0);
  assert.equal(host.sleepers.size, 0);
  assert.equal(workers[0].terminated, true);
});

test("seek discards metadata completion from the previous generation", async () => {
  const { host, workers, states } = fixture({ deferOpen: true });
  const seek = host.seek(0);
  workers[0].reply(workers[0].messages[0], {
    duration: 99,
    video: false,
    audio: false,
  });
  workers[1].reply(workers[1].messages[0], {
    duration: 10,
    video: false,
    audio: false,
  });
  await seek;
  assert.equal(host.duration, 10);
  assert.equal(states.at(-1).status, "paused");
  assert.equal(workers[0].terminated, true);
  await host.close();
});

test("future video frame bounds decoder requests while clock is stationary", async (t) => {
  const { host, workers } = fixture();
  t.after(() => host.close());
  await host.ready;
  await host.play();
  await flush();
  assert.equal(workers[0].messages.filter((m) => m.op === "video").length, 2);
  assert.equal(host.pending.size, 0);
  assert.equal(host.sleepers.size, 1);
  await host.close();
  assert.equal(host.sleepers.size, 0);
});

test("audio context construction failure becomes visible failure and releases worker", async () => {
  const { host, workers, states } = fixture({
    audio: true,
    createAudio: () => {
      throw new Error("Unavailable");
    },
  });
  await host.ready;
  await host.play();
  assert.equal(states.at(-1).status, "error");
  assert.match(states.at(-1).error, /Media playback failed/);
  assert.equal(workers[0].terminated, true);
  await host.close();
});

test("missing audio output fails before waiting for context resume", async (t) => {
  let resumes = 0;
  const context = {
    state: "suspended",
    currentTime: 0,
    destination: { maxChannelCount: 0 },
    createGain: () => ({ connect() {}, disconnect() {}, gain: {} }),
    async resume() {
      resumes++;
    },
    async close() {
      this.state = "closed";
    },
  };
  const { host, workers, states } = fixture({
    audio: true,
    createAudio: () => context,
  });
  t.after(() => host.close());
  await host.ready;
  await host.play();
  assert.equal(resumes, 0);
  assert.equal(states.at(-1).status, "error");
  assert.match(states.at(-1).error, /Media playback failed/);
  assert.equal(workers[0].terminated, true);
  await host.close();
  assert.equal(context.state, "closed");
  assert.ok(states.every((state) => state.status !== "playing"));
});

test("close during audio unlock prevents stale playback and closes the context", async () => {
  let unlock;
  const context = {
    currentTime: 0,
    state: "suspended",
    destination: {},
    createGain: () => ({ connect() {}, disconnect() {}, gain: {} }),
    resume: () =>
      new Promise((resolve) => {
        unlock = resolve;
      }),
    async close() {
      this.state = "closed";
    },
  };
  const { host, states } = fixture({ audio: true, createAudio: () => context });
  await host.ready;
  const playing = host.play();
  await host.close();
  unlock();
  await playing;
  assert.equal(host.status, "closed");
  assert.equal(context.state, "closed");
  assert.ok(states.every((s) => s.status !== "playing"));
});

test("worker construction failure becomes a visible error without rejecting readiness", async () => {
  const states = [];
  const host = new MediaHost(
    "https://example.invalid/media",
    (s) => states.push(s),
    () => {},
    {
      createWorker: () => {
        throw new Error("CSP blocked worker");
      },
    },
  );
  await host.ready;
  assert.equal(states.at(-1).status, "error");
  assert.equal(host.pending.size, 0);
  assert.equal(host.sleepers.size, 0);
  await host.close();
});

test("buffering suspends the audio clock before resuming scheduled playback", async () => {
  const { host } = fixture();
  await host.ready;
  const calls = [];
  host.context = {
    currentTime: 1,
    async suspend() {
      calls.push("suspend");
    },
    async resume() {
      calls.push("resume");
    },
    async close() {
      calls.push("close");
    },
  };
  host.clockStart = 0;
  host.audioEnded = false;
  host.audioEnd = 1.02;
  host.status = "playing";
  host.intent = "playing";
  host.tick(host.generation);
  assert.equal(host.status, "buffering");
  assert.equal(host.currentTime(), 1);
  await host.resumeBuffer(host.generation);
  assert.equal(host.status, "playing");
  assert.deepEqual(calls, ["suspend", "resume"]);
  await host.close();
});

test("hidden page pauses playback and returning does not resume automatically", async () => {
  const { host, states } = fixture();
  await host.ready;
  await host.play();
  globalThis.document = { hidden: true, removeEventListener() {} };
  try {
    host.visibility();
    await host.ready;
    assert.equal(states.at(-1).status, "paused");
    document.hidden = false;
    host.visibility();
    assert.equal(host.status, "paused");
  } finally {
    await host.close();
    delete globalThis.document;
  }
});

test("deferred Host allocates no Worker before lifecycle start", async () => {
  let created = 0;
  const host = new MediaHost(
    "https://example.invalid/media",
    () => {},
    () => {},
    {
      autoStart: false,
      createWorker: () => {
        created++;
        throw new Error("blocked");
      },
    },
  );
  assert.equal(host.status, "idle");
  assert.equal(created, 0);
  await host.start();
  assert.equal(created, 1);
  assert.equal(host.status, "error");
  await host.close();
});

test("seek trimming schedules the retained audio at the seek clock origin", async () => {
  const { host } = fixture();
  await host.ready;
  const starts = [];
  host.context = {
    currentTime: 0.92,
    createBuffer: () => ({ copyToChannel() {} }),
    createBufferSource: () => ({
      connect() {},
      disconnect() {},
      stop() {},
      start: (...args) => starts.push(args),
    }),
    async close() {},
  };
  host.gain = { disconnect() {} };
  host.position = 3;
  host.clockStart = 1;
  host.status = "playing";
  host.audioEnded = false;
  host.audioEnd = 3;
  host.firstAudio = {
    channels: [new Float32Array(960)],
    sampleRate: 48000,
    timestamp: 2.99,
    duration: 0.02,
  };
  await host.pumpAudio(host.generation);
  assert.equal(starts.length, 1);
  assert.ok(Math.abs(starts[0][0] - 1) < 1e-8);
  assert.ok(Math.abs(starts[0][1] - 0.01) < 1e-8);
  assert.equal(host.metrics.lateAudioSamples, 0);
  await host.close();
});

test("latest seek preserves playing intent after an earlier seek is cancelled", async (t) => {
  const { host, workers } = fixture({ deferOpen: true });
  t.after(() => host.close());
  workers[0].reply(workers[0].messages[0], {
    duration: 10,
    video: false,
    audio: false,
  });
  await host.ready;
  await host.play();
  const first = host.seek(1);
  const second = host.seek(2);
  await flush();
  workers[2].reply(workers[2].messages[0], {
    duration: 10,
    video: false,
    audio: false,
  });
  await Promise.all([first, second]);
  assert.equal(host.status, "playing");
  assert.equal(host.position, 2);
});

test("pause during seek cancels pending playback intent", async (t) => {
  const { host, workers } = fixture({ deferOpen: true });
  t.after(() => host.close());
  workers[0].reply(workers[0].messages[0], {
    duration: 10,
    video: false,
    audio: false,
  });
  await host.ready;
  await host.play();
  const seek = host.seek(2);
  host.pause();
  await flush();
  workers[2].reply(workers[2].messages[0], {
    duration: 10,
    video: false,
    audio: false,
  });
  await host.ready;
  await seek;
  assert.equal(host.status, "paused");
  assert.equal(host.intent, "paused");
});

test("late buffering resume cannot replace the ended state", async (t) => {
  const { host } = fixture();
  t.after(() => host.close());
  await host.ready;
  let resume;
  host.context = {
    resume: () => new Promise((resolve) => (resume = resolve)),
    async close() {},
  };
  host.status = "buffering";
  host.intent = "playing";
  host.bufferingTask = Promise.resolve();
  const pending = host.resumeBuffer(host.generation);
  await flush();
  host.status = "ended";
  host.intent = "paused";
  resume();
  await pending;
  assert.equal(host.status, "ended");
});

test(
  "short audio blocks resume when the bounded node queue is full",
  { timeout: 1000 },
  async (t) => {
    const { host } = fixture();
    t.after(() => host.close());
    await host.ready;
    let resumed;
    const resumeEvent = new Promise((resolve) => (resumed = resolve));
    host.context = {
      currentTime: 0,
      createBuffer: () => ({ copyToChannel() {} }),
      createBufferSource: () => ({
        connect() {},
        disconnect() {},
        stop() {},
        start() {},
      }),
      async resume() {
        resumed();
      },
      async close() {},
    };
    host.gain = { disconnect() {} };
    host.status = "buffering";
    host.intent = "playing";
    host.audioEnded = false;
    host.audioEnd = 0;
    host.clockStart = 0;
    host.bufferingTask = Promise.resolve();
    let block = 0;
    host.rpc = async () => ({
      channels: [new Float32Array(120)],
      sampleRate: 48000,
      timestamp: block++ * 0.0025,
      duration: 0.0025,
    });
    host.audioTask = host.pumpAudio(host.generation);
    await resumeEvent;
    await flush();
    assert.equal(host.status, "playing");
    assert.equal(host.nodes.size, 64);
    assert.ok(host.audioEnd < 0.3);
  },
);

test("rapid mute toggles use owned state and retain volume", async (t) => {
  const { host } = fixture();
  t.after(() => host.close());
  await host.ready;
  host.setVolume(0.25);
  host.toggleMute();
  assert.equal(host.muted, true);
  host.toggleMute();
  assert.equal(host.muted, false);
  assert.equal(host.volume, 0.25);
});
test("rapid playback toggle cancels an in-flight audio unlock", async (t) => {
  let resume;
  const context = {
    currentTime: 0,
    destination: {},
    state: "suspended",
    createGain: () => ({ connect() {}, disconnect() {}, gain: {} }),
    resume: () => new Promise((resolve) => (resume = resolve)),
    async close() {
      this.state = "closed";
    },
  };
  const { host } = fixture({ audio: true, createAudio: () => context });
  t.after(() => host.close());
  await host.ready;
  const first = host.togglePlayback();
  const second = host.togglePlayback();
  await host.ready;
  resume();
  await Promise.all([first, second]);
  assert.equal(host.status, "paused");
  assert.equal(host.intent, "paused");
});

test("Wasm default factory supports deferred lifecycle without allocating a Worker", async () => {
  const host = createWasmHost(
    "https://example.invalid/media",
    () => {},
    () => {},
    false,
  );
  assert.equal(host.status, "idle");
  assert.equal(host.worker, null);
  await host.close();
});

test("media failure remains visible after mute and volume actions", async (t) => {
  const { host, states } = fixture({
    audio: true,
    createAudio: () => {
      throw new Error("Unavailable");
    },
  });
  t.after(() => host.close());
  await host.ready;
  await host.play();
  const failure = states.at(-1).error;
  assert.ok(failure);
  host.toggleMute();
  host.setVolume(0.25);
  assert.equal(states.at(-1).status, "error");
  assert.equal(states.at(-1).error, failure);
});

test("GPU generation cancellation closes the queued decoder frame", async () => {
  const closed = [];
  const { host, frames, workers } = fixture({
    path: "gpu-texture",
    makeFrame: () => {
      const id = closed.length;
      closed.push(0);
      return {
        frame: {
          close() {
            closed[id]++;
          },
        },
        width: 1,
        height: 1,
        timestamp: 5,
        duration: 1,
        readbackMs: 0,
      };
    },
  });
  await host.ready;
  assert.equal(workers[0].messages[0].path, "gpu-texture");
  await host.play();
  await flush();
  await host.close();
  assert.equal(closed[1], 1);
  assert.equal(closed[0], 0); // Ownership of the presented preview belongs to the adapter.
  frames[0].frame.close();
  assert.equal(host.metrics.copiedBytes, 0);
});

test("GPU transfer arriving after close is released instead of published", async () => {
  const { host, workers, frames } = fixture({
    deferOpen: true,
    path: "gpu-texture",
  });
  await host.close();
  let closed = 0;
  workers[0].onmessage({
    data: {
      id: 999,
      result: {
        frame: {
          close() {
            closed++;
          },
        },
      },
    },
  });
  assert.equal(closed, 1);
  assert.equal(frames.length, 0);
});

test("terminal rendering failure closes an existing audio graph", async () => {
  let disconnected = 0;
  const context = {
    currentTime: 0,
    state: "suspended",
    destination: {},
    createGain: () => ({
      connect() {},
      disconnect() {
        disconnected++;
      },
      gain: {},
    }),
    async resume() {
      this.state = "running";
    },
    async close() {
      this.state = "closed";
    },
  };
  const { host } = fixture({ audio: true, createAudio: () => context });
  await host.ready;
  await host.play();
  host.failRendering();
  await host.audioClosing;
  assert.equal(host.status, "error");
  assert.equal(context.state, "closed");
  assert.equal(context.onstatechange, null);
  assert.equal(disconnected, 1);
  assert.equal(host.nodes.size, 0);
  assert.equal(host.worker, null);
  await host.close();
  assert.equal(disconnected, 1);
});

test("seek cancels a blocked preview without publishing its stale generation", async () => {
  const produced = [],
    scheduler = controlledScheduler();
  const { host, frames } = fixture({
    path: "gpu-texture",
    scheduler,
    makeFrame: () => {
      const frame = ownedFrame();
      produced.push(frame);
      return frame;
    },
    acceptFrame: (frame) => frame.generation > 1,
  });
  const oldReady = host.ready;
  await flush();
  assert.equal(host.status, "loading");
  assert.equal(host.sleepers.size, 1);
  await host.seek(4);
  await oldReady;
  assert.equal(host.status, "paused");
  assert.equal(host.position, 4);
  assert.equal(produced[0].closes, 1);
  assert.equal(frames.length, 1);
  assert.equal(frames[0].generation, 2);
  assert.equal(produced[1].closes, 0);
  await host.close();
  produced[1].frame.close();
  assert.equal(scheduler.tasks.size, 0);
});

test("a preview callback failure releases the preview once and becomes visible failure", async () => {
  const produced = [];
  const { host } = fixture({
    makeFrame: () => {
      const frame = ownedFrame();
      produced.push(frame);
      return frame;
    },
    acceptFrame: () => {
      throw new Error("preview failed");
    },
  });
  await host.ready;
  assert.equal(host.status, "error");
  assert.equal(produced[0].closes, 1);
  await host.close();
  assert.equal(produced[0].closes, 1);
});

test("video EOF preserves trailing skip counts without delivering or counting another frame", async (t) => {
  let reads = 0;
  const { host, frames } = fixture({
    makeFrame: () =>
      reads++ === 0
        ? {
            pixels: new ArrayBuffer(4),
            width: 1,
            height: 1,
            timestamp: 0,
            duration: 0.03,
            readbackMs: 1.5,
          }
        : { ended: true, skippedBeforeConversion: 3 },
  });
  t.after(() => host.close());
  await host.ready;
  await host.play();
  await flush();
  assert.equal(host.videoEnded, true);
  assert.equal(host.metrics.skippedBeforeConversion, 3);
  assert.equal(host.metrics.decoded, 1);
  assert.equal(host.metrics.acceptedFrames, 1);
  assert.equal(host.metrics.copiedBytes, 4);
  assert.equal(host.metrics.readbackMs, 1.5);
  assert.equal(frames.length, 1);
});

test("EOF during preview preserves skips without publishing an empty video frame", async (t) => {
  const { host, frames } = fixture({
    makeFrame: () => ({ ended: true, skippedBeforeConversion: 2 }),
  });
  t.after(() => host.close());
  await host.ready;
  assert.equal(host.status, "paused");
  assert.equal(host.metrics.skippedBeforeConversion, 2);
  assert.equal(host.metrics.decoded, 0);
  assert.equal(host.metrics.acceptedFrames, 0);
  assert.equal(host.metrics.copiedBytes, 0);
  assert.equal(host.metrics.readbackMs, 0);
  assert.deepEqual(frames, []);
});
