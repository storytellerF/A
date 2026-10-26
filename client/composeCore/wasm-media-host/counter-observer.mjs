/* This is a private project. All rights reserved. */
let reader,
  task,
  running = false,
  frames = [],
  error = "",
  closedFrames = 0;
self.onmessage = async ({ data }) => {
  try {
    let result;
    if (data.op === "start") {
      const { rect, scale } = data;
      reader = data.readable.getReader();
      running = true;
      task = (async () => {
        try {
          while (running) {
            const { value: frame, done } = await reader.read();
            if (done) break;
            try {
              const options = { format: "RGBA", rect },
                pixels = new Uint8Array(frame.allocationSize(options));
              const copyStart = performance.now();
              await frame.copyTo(pixels, options);
              const copyMs = performance.now() - copyStart;
              let value = 0;
              for (let bit = 0; bit < 16; bit++) {
                const i = Math.floor((bit * 8 + 4) * scale) * 4;
                if (pixels[i] + pixels[i + 1] + pixels[i + 2] > 384)
                  value |= 1 << bit;
              }
              frames.push({
                id: value,
                copyMs,
                timestamp: frame.timestamp,
                epoch: performance.timeOrigin + performance.now(),
              });
            } finally {
              frame.close();
              closedFrames++;
            }
          }
        } catch (e) {
          if (running) error = e.message;
        }
      })();
      result = { ready: true };
    } else if (data.op === "reset") {
      frames = [];
      result = { ready: true };
    } else if (data.op === "snapshot") result = { frames, error, closedFrames };
    else if (data.op === "stop") {
      running = false;
      await reader.cancel();
      await task;
      result = { frames, error, closedFrames };
    } else throw new Error("Unknown observer action");
    self.postMessage({ id: data.id, result });
  } catch (e) {
    self.postMessage({ id: data.id, error: e.message });
  }
};
