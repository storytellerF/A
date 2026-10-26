/* This is a private project. All rights reserved. */
export async function installMediaDiagnostics(
  page,
  { frameStages = false } = {},
) {
  await page.addInitScript((frameStages) => {
    window.mediaTest = {
      workers: [],
      contexts: [],
      gains: [],
      connected: new Set(),
      decoded: [],
      audio: [],
      uploads: [],
      transferredFrames: new Set(),
      frameTextures: new Set(),
      deletedFrameTextures: 0,
      frameCloses: 0,
      closedWithoutUpload: [],
      frameCanvases: new Set(),
      peakFrames: 0,
      peakTextures: 0,
    };
    const BaseWorker = Worker;
    window.Worker = class extends BaseWorker {
      constructor(...args) {
        super(...args);
        mediaTest.workers.push(this);
        this.closed = false;
        this.addEventListener("message", ({ data }) => {
          if (data.result?.channels)
            mediaTest.audio.push({
              worker: mediaTest.workers.indexOf(this),
              time: performance.now(),
            });
          if (data.result?.frame) {
            mediaTest.transferredFrames.add(data.result.frame);
            mediaTest.peakFrames = Math.max(
              mediaTest.peakFrames,
              mediaTest.transferredFrames.size,
            );
          }
          if (data.result?.pixels || data.result?.frame)
            mediaTest.decoded.push({
              worker: mediaTest.workers.indexOf(this),
              time: performance.now(),
              timestamp: data.result.timestamp,
              width: data.result.width,
              height: data.result.height,
              readbackMs: data.result.readbackMs,
              skippedBeforeConversion: data.result.skippedBeforeConversion ?? 0,
              bytes: data.result.pixels?.byteLength ?? 0,
            });
        });
      }
      terminate() {
        this.closed = true;
        super.terminate();
      }
    };
    const BaseAudio = AudioContext;
    window.AudioContext = class extends BaseAudio {
      constructor(...args) {
        super(...args);
        mediaTest.contexts.push(this);
      }
      createGain() {
        const node = super.createGain();
        mediaTest.gains.push(node);
        return node;
      }
      createBufferSource() {
        const node = super.createBufferSource();
        const connect = node.connect.bind(node),
          disconnect = node.disconnect.bind(node);
        node.connect = (...args) => {
          mediaTest.connected.add(node);
          return connect(...args);
        };
        node.disconnect = (...args) => {
          mediaTest.connected.delete(node);
          return disconnect(...args);
        };
        return node;
      }
    };
    const uploadedFrames = new WeakSet();
    const closeFrame = VideoFrame.prototype.close;
    VideoFrame.prototype.close = function () {
      if (mediaTest.transferredFrames.delete(this)) {
        mediaTest.frameCloses++;
        if (frameStages && !uploadedFrames.has(this))
          mediaTest.closedWithoutUpload.push({
            timestamp: this.timestamp / 1e6,
            time: performance.now(),
            stack: new Error("Frame closed before texture upload").stack,
          });
      }
      return closeFrame.call(this);
    };
    const bindings = new WeakMap();
    for (const cls of [WebGLRenderingContext, WebGL2RenderingContext]) {
      const activeTexture = cls.prototype.activeTexture;
      const bindTexture = cls.prototype.bindTexture;
      const bindingState = (gl) => {
        let state = bindings.get(gl);
        if (!state) {
          state = { active: gl.TEXTURE0, textures: new Map() };
          bindings.set(gl, state);
        }
        return state;
      };
      cls.prototype.activeTexture = function (unit) {
        bindingState(this).active = unit;
        return activeTexture.call(this, unit);
      };
      cls.prototype.bindTexture = function (target, texture) {
        const state = bindingState(this);
        state.textures.set(`${state.active}:${target}`, texture);
        return bindTexture.call(this, target, texture);
      };
      const deleteTexture = cls.prototype.deleteTexture;
      cls.prototype.deleteTexture = function (texture) {
        if (mediaTest.frameTextures.delete(texture))
          mediaTest.deletedFrameTextures++;
        return deleteTexture.call(this, texture);
      };
      for (const method of ["texImage2D", "texSubImage2D"]) {
        const original = cls.prototype[method];
        cls.prototype[method] = function (...args) {
          const source = args.at(-1);
          const frame = source instanceof VideoFrame;
          const w = frame
            ? source.displayWidth
            : method === "texImage2D"
              ? args[3]
              : args[4];
          const h = frame
            ? source.displayHeight
            : method === "texImage2D"
              ? args[4]
              : args[5];
          const start = performance.now();
          const result = original.apply(this, args);
          if (!mediaTest.glInfo) {
            const info = this.getExtension("WEBGL_debug_renderer_info");
            mediaTest.glInfo = {
              renderer: this.getParameter(
                info?.UNMASKED_RENDERER_WEBGL ?? this.RENDERER,
              ),
              version: this.getParameter(this.VERSION),
            };
          }
          if (frame) {
            uploadedFrames.add(source);
            const state = bindingState(this);
            mediaTest.frameTextures.add(
              state.textures.get(`${state.active}:${this.TEXTURE_2D}`),
            );
            mediaTest.frameCanvases.add(this.canvas);
            mediaTest.peakTextures = Math.max(
              mediaTest.peakTextures,
              mediaTest.frameTextures.size,
            );
          }
          if (typeof w === "number" && typeof h === "number")
            mediaTest.uploads.push({
              method,
              w,
              h,
              frame,
              mediaTime: frame ? source.timestamp / 1e6 : null,
              ms: performance.now() - start,
              time: performance.now(),
            });
          return result;
        };
      }
    }
  }, frameStages);
}
