/* This is a private project. All rights reserved. */
import {
  Input,
  UrlSource,
  ALL_FORMATS,
  VideoSampleSink,
  AudioSampleSink,
} from "mediabunny";

import { readCurrentVideoSample } from "./video-sample.mjs";
let input, video, audio, raster, rasterContext;
let path = "rgba-copy";
const lanes = {
  control: Promise.resolve(),
  video: Promise.resolve(),
  audio: Promise.resolve(),
};
// Host creates a new worker for every seek/source generation. Termination cancels
// outstanding reads and destroys the generation's native decoder resources.
self.onmessage = ({ data }) => {
  const lane = data.op === "video" || data.op === "audio" ? data.op : "control";
  lanes[lane] = lanes[lane]
    .then(async () => {
      const result = await execute(data);
      const transfer = result?.pixels
        ? [result.pixels]
        : result?.frame
          ? [result.frame]
          : (result?.channels?.map((c) => c.buffer) ?? []);
      self.postMessage({ id: data.id, result }, transfer);
    })
    .catch((error) =>
      self.postMessage({ id: data.id, error: error?.message ?? String(error) }),
    );
};

async function execute({ op, url, time, path: requestedPath }) {
  if (op === "open") {
    if (!self.isSecureContext)
      throw new Error(
        "WebCodecs requires a secure context (HTTPS or localhost).",
      );
    input = new Input({
      source: new UrlSource(url, {
        maxCacheSize: 8 * 1024 * 1024,
        getRetryDelay: () => null,
      }),
      formats: ALL_FORMATS,
    });
    const vt = await input.getPrimaryVideoTrack();
    const at = await input.getPrimaryAudioTrack();
    if (!vt && !at) throw new Error("No playable video or audio track.");
    for (const track of [vt, at].filter(Boolean)) {
      if (!(await track.canDecode()))
        throw new Error(
          `Unsupported browser codec: ${await track.getCodecParameterString()}`,
        );
    }
    path = requestedPath ?? "rgba-copy";
    video = vt ? new VideoSampleSink(vt).samples(time) : null;
    audio = at ? new AudioSampleSink(at).samples(time) : null;
    return {
      duration: await input.computeDuration(),
      video: !!vt,
      audio: !!at,
      videoCodec: vt ? await vt.getCodecParameterString() : "",
      audioCodec: at ? await at.getCodecParameterString() : "",
    };
  }
  if (op === "video") {
    const { sample, skipped } = await readCurrentVideoSample(video, time);
    if (!sample) return { ended: true, skippedBeforeConversion: skipped };
    try {
      if (path === "gpu-texture") {
        const frame = sample.toVideoFrame();
        return {
          frame,
          width: frame.displayWidth,
          height: frame.displayHeight,
          timestamp: sample.timestamp,
          duration: sample.duration,
          rotation: sample.rotation,
          flip: sample.flip,
          readbackMs: 0,
          skippedBeforeConversion: skipped,
        };
      }
      const width = sample.displayWidth,
        height = sample.displayHeight;
      if (!raster || raster.width !== width || raster.height !== height) {
        raster = new OffscreenCanvas(width, height);
        // Match Mediabunny's opaque-canvas policy and its Firefox VideoFrame workaround.
        rasterContext = raster.getContext("2d", {
          alpha: /Firefox\//.test(navigator.userAgent),
        });
      }
      rasterContext.clearRect(0, 0, width, height);
      sample.draw(rasterContext, 0, 0, width, height);
      const start = performance.now();
      const pixels = rasterContext.getImageData(0, 0, width, height).data
        .buffer;
      return {
        pixels,
        width,
        height,
        timestamp: sample.timestamp,
        duration: sample.duration,
        readbackMs: performance.now() - start,
        skippedBeforeConversion: skipped,
      };
    } finally {
      sample.close();
    }
  }
  if (op === "audio") {
    const next = audio ? await audio.next() : { done: true };
    if (next.done) return null;
    const sample = next.value;
    try {
      const channels = Array.from(
        { length: sample.numberOfChannels },
        (_, planeIndex) => {
          const channel = new Float32Array(sample.numberOfFrames);
          sample.copyTo(channel, { planeIndex, format: "f32-planar" });
          return channel;
        },
      );
      return {
        channels,
        sampleRate: sample.sampleRate,
        timestamp: sample.timestamp,
        duration: sample.duration,
      };
    } finally {
      sample.close();
    }
  }
  throw new Error(`Unknown media operation: ${op}`);
}
