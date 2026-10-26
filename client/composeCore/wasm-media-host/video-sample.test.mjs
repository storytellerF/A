/* This is a private project. All rights reserved. */
import test from "node:test";
import assert from "node:assert/strict";
import { readCurrentVideoSample } from "./video-sample.mjs";
function sample(timestamp, duration = 0.03) {
  return {
    timestamp,
    duration,
    closed: 0,
    close() {
      this.closed++;
    },
  };
}
function iterator(samples) {
  let index = 0;
  return {
    async next() {
      return index < samples.length
        ? { done: false, value: samples[index++] }
        : { done: true };
    },
  };
}
test("late samples close before conversion while the selected frame remains owned by the caller", async () => {
  const old = sample(0),
    older = sample(0.2),
    current = sample(1);
  const result = await readCurrentVideoSample(
    iterator([old, older, current]),
    1,
  );
  assert.equal(result.skipped, 2);
  assert.equal(result.sample, current);
  assert.equal(old.closed, 1);
  assert.equal(older.closed, 1);
  assert.equal(current.closed, 0);
});
test("the original fifty millisecond lateness boundary is retained", async () => {
  const late = sample(0.899, 0.05),
    boundary = sample(0.9, 0.05);
  const result = await readCurrentVideoSample(iterator([late, boundary]), 1);
  assert.equal(result.sample, boundary);
  assert.equal(late.closed, 1);
  assert.equal(boundary.closed, 0);
});
test("end of input releases every skipped sample", async () => {
  const first = sample(0),
    last = sample(0.1);
  const result = await readCurrentVideoSample(iterator([first, last]), 2);
  assert.deepEqual(result, { sample: null, skipped: 2 });
  assert.equal(first.closed, 1);
  assert.equal(last.closed, 1);
});
test("sample iteration failures propagate after releasing previously skipped frames", async () => {
  const first = sample(0),
    failure = new Error("decoder failed");
  let read = false;
  const source = {
    async next() {
      if (read) throw failure;
      read = true;
      return { done: false, value: first };
    },
  };
  await assert.rejects(
    readCurrentVideoSample(source, 1),
    (error) => error === failure,
  );
  assert.equal(first.closed, 1);
});
