/* This is a private project. All rights reserved. */
import test from "node:test";
import assert from "node:assert/strict";
import { GpuBridge } from "./gpu-bridge.mjs";
function source(generation = 1) {
  let closes = 0;
  return {
    generation,
    width: 320,
    height: 180,
    rotation: 0,
    flip: false,
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
function context({ reject = false } = {}) {
  const names = [
    "ACTIVE_TEXTURE",
    "TEXTURE0",
    "TEXTURE_BINDING_2D",
    "PIXEL_UNPACK_BUFFER_BINDING",
    "UNPACK_ALIGNMENT",
    "UNPACK_FLIP_Y_WEBGL",
    "UNPACK_PREMULTIPLY_ALPHA_WEBGL",
    "UNPACK_COLORSPACE_CONVERSION_WEBGL",
    "UNPACK_ROW_LENGTH",
    "UNPACK_IMAGE_HEIGHT",
    "UNPACK_SKIP_PIXELS",
    "UNPACK_SKIP_ROWS",
    "UNPACK_SKIP_IMAGES",
    "PIXEL_UNPACK_BUFFER",
    "TEXTURE_2D",
    "TEXTURE_MIN_FILTER",
    "TEXTURE_MAG_FILTER",
    "TEXTURE_WRAP_S",
    "TEXTURE_WRAP_T",
    "LINEAR",
    "CLAMP_TO_EDGE",
    "RGBA",
    "UNSIGNED_BYTE",
  ];
  const gl = Object.fromEntries(names.map((x, i) => [x, i + 1]));
  const state = new Map(names.map((x, i) => [gl[x], i + 20]));
  state.set(gl.TEXTURE_BINDING_2D, { old: true });
  const original = new Map(state),
    listeners = new Map(),
    deleted = [];
  Object.assign(gl, {
    NO_ERROR: 0,
    canvas: {
      addEventListener(n, f) {
        listeners.set(n, f);
      },
      removeEventListener(n) {
        listeners.delete(n);
      },
    },
    isContextLost: () => false,
    getParameter: (x) => state.get(x),
    activeTexture: (x) => state.set(gl.ACTIVE_TEXTURE, x),
    bindBuffer: (_, x) => state.set(gl.PIXEL_UNPACK_BUFFER_BINDING, x),
    bindTexture: (_, x) => state.set(gl.TEXTURE_BINDING_2D, x),
    pixelStorei: (x, v) => state.set(x, v),
    createTexture: () => ({ texture: true }),
    deleteTexture: (x) => deleted.push(x),
    texParameteri() {},
    texImage2D(...args) {
      this.source = args.at(-1);
    },
    getError: () => (reject ? 1282 : 0),
  });
  return { gl, state, original, listeners, deleted };
}
test("frame upload restores all shared GL unpack/binding state and closes the source", () => {
  const bridge = new GpuBridge(),
    f = source(),
    c = context();
  bridge.offer(f);
  const transfer = bridge.upload(c.gl);
  assert.equal(c.gl.source, f.frame);
  assert.deepEqual(c.state, c.original);
  assert.equal(f.closes, 1);
  assert.equal(bridge.hasPending, false);
  assert.equal(c.deleted.length, 0);
  bridge.deleteUnadopted(transfer.texture);
  assert.equal(c.deleted.length, 1);
  bridge.close();
  assert.equal(c.listeners.size, 0);
});
test("a busy renderer retains its frame and leaves rejected frame ownership with the caller", () => {
  const bridge = new GpuBridge(),
    a = source(),
    b = source(),
    c = context();
  assert.equal(bridge.offer(a), true);
  assert.equal(bridge.offer(b), false);
  assert.equal(a.closes, 0);
  assert.equal(b.closes, 0);
  const first = bridge.upload(c.gl);
  assert.equal(c.gl.source, a.frame);
  assert.equal(bridge.offer(b), true);
  const second = bridge.upload(c.gl);
  assert.equal(c.gl.source, b.frame);
  assert.equal(a.closes, 1);
  assert.equal(b.closes, 1);
  bridge.deleteUnadopted(first.texture);
  bridge.deleteUnadopted(second.texture);
  bridge.close();
});

test("a new generation cancels pending old content and stale offers remain caller owned", () => {
  const bridge = new GpuBridge(),
    old = source(1),
    current = source(2),
    stale = source(1);
  assert.equal(bridge.offer(old), true);
  assert.equal(bridge.offer(current), true);
  assert.equal(old.closes, 1);
  assert.equal(current.closes, 0);
  assert.equal(bridge.offer(stale), false);
  assert.equal(stale.closes, 0);
  bridge.close();
  assert.equal(current.closes, 1);
  stale.frame.close();
});

test("a closed renderer rejects without acquiring frame ownership", () => {
  const bridge = new GpuBridge(),
    frame = source();
  bridge.close();
  assert.equal(bridge.offer(frame), false);
  assert.equal(frame.closes, 0);
  frame.frame.close();
});

test("consumption is acknowledged after the current draw scope unwinds", async () => {
  const bridge = new GpuBridge(),
    frame = source(),
    c = context();
  let consumed = 0;
  frame.onConsumed = () => consumed++;
  bridge.offer(frame);
  const transfer = bridge.upload(c.gl);
  assert.equal(consumed, 0);
  assert.equal(frame.closes, 1);
  await Promise.resolve();
  assert.equal(consumed, 1);
  bridge.deleteUnadopted(transfer.texture);
  bridge.close();
});

test("failed GL upload deletes the unadopted texture, restores state and releases the frame", () => {
  const bridge = new GpuBridge(),
    f = source(),
    c = context({ reject: true });
  bridge.offer(f);
  assert.equal(bridge.upload(c.gl), null);
  assert.ok(bridge.error);
  assert.equal(f.closes, 1);
  assert.equal(c.deleted.length, 1);
  assert.deepEqual(c.state, c.original);
  bridge.close();
});
test("context loss releases pending frames and notifies the owner exactly once", () => {
  let failures = 0;
  const bridge = new GpuBridge(() => {
      failures++;
      bridge.close();
    }),
    a = source(),
    b = source(),
    c = context();
  bridge.offer(a);
  const texture = bridge.upload(c.gl);
  bridge.offer(b);
  c.listeners.get("webglcontextlost")();
  assert.equal(failures, 1);
  assert.equal(b.closes, 1);
  assert.equal(c.listeners.size, 0);
  bridge.deleteUnadopted(texture.texture);
});
test("a different renderer context cannot consume a queued frame", () => {
  const bridge = new GpuBridge(),
    a = source(),
    b = source(),
    first = context(),
    second = context();
  bridge.offer(a);
  const texture = bridge.upload(first.gl);
  bridge.offer(b);
  assert.equal(bridge.upload(second.gl), null);
  assert.equal(b.closes, 1);
  assert.ok(bridge.error);
  assert.equal(second.gl.source, undefined);
  bridge.deleteUnadopted(texture.texture);
  bridge.close();
});
