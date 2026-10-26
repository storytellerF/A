/* This is a private project. All rights reserved. */
export const MAX_VIDEO_LATENESS = 0.05;
// Cull expired samples before expensive canvas conversion/readback or transfer.
// The selected sample belongs to the caller; every skipped sample closes here.
export async function readCurrentVideoSample(iterator, time = -Infinity) {
  let skipped = 0;
  while (iterator) {
    const next = await iterator.next();
    if (next.done) return { sample: null, skipped };
    const sample = next.value;
    if (sample.timestamp + sample.duration < time - MAX_VIDEO_LATENESS) {
      sample.close();
      skipped++;
    } else return { sample, skipped };
  }
  return { sample: null, skipped };
}
