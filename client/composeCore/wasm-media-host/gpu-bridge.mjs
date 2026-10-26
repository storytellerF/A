/* This is a private project. All rights reserved. */
// Owns only transferable frames and external GL uploads; Kotlin owns adopted Skia Images.
export default function createGpuBridge(onFailure) {
  const parameters = new URLSearchParams(globalThis.location?.search ?? "");
  return new GpuBridge(
    onFailure,
    parameters.get("gpuImport") ?? "restored",
    parameters.get("mediaProfile") === "true",
  );
}
export class GpuBridge {
  constructor(onFailure = () => {}, strategy = "restored", profiling = false) {
    if (!["legacy", "restored"].includes(strategy))
      throw new Error("Unknown GPU import strategy");
    this.legacySynchronization = strategy === "legacy";
    this.profiling = profiling;
    this.stageStarts = new Map();
    this.onFailure = onFailure;
    this.pending = null;
    this.generation = -1;
    this.gl = null;
    this.closed = false;
    this.error = "";
    this.metrics = { uploads: 0, uploadMs: 0, closedFrames: 0 };
    this.contextLost = () => {
      if (this.closed) return;
      this.error = "WebGL context lost";
      this.releasePending();
      this.onFailure();
    };
  }
  beginStage(name) {
    this.stageStarts.set(name, performance.now());
  }
  endStage(name) {
    performance.measure(`media.gpu.${name}`, {
      start: this.stageStarts.get(name),
      end: performance.now(),
    });
    this.stageStarts.delete(name);
  }
  get hasPending() {
    return this.pending !== null;
  }
  // False leaves ownership with the caller. Only a newer generation may cancel
  // an unconsumed frame (seek/pause/source supersession), never normal playback.
  offer(frame) {
    if (!Number.isSafeInteger(frame.generation) || frame.generation < 0)
      throw new Error("A media generation is required");
    if (this.closed || this.error || frame.generation < this.generation)
      return false;
    if (this.pending && frame.generation === this.generation) return false;
    if (frame.generation > this.generation) this.releasePending();
    this.generation = frame.generation;
    this.pending = frame;
    return true;
  }
  releasePending() {
    if (this.pending) {
      this.pending.frame.close();
      this.pending = null;
      this.metrics.closedFrames++;
    }
  }
  upload(gl) {
    if (this.closed || this.error || !this.pending) return null;
    if (this.gl && this.gl !== gl) {
      this.error = "Renderer context changed";
      this.releasePending();
      return null;
    }
    if (gl.isContextLost()) {
      this.contextLost();
      return null;
    }
    if (!this.gl) {
      this.gl = gl;
      gl.canvas.addEventListener("webglcontextlost", this.contextLost);
    }
    const source = this.pending;
    this.pending = null;
    if (this.profiling) this.beginStage("state-save");
    const active = gl.getParameter(gl.ACTIVE_TEXTURE);
    gl.activeTexture(gl.TEXTURE0);
    const binding = gl.getParameter(gl.TEXTURE_BINDING_2D);
    const unpackBuffer = gl.getParameter(gl.PIXEL_UNPACK_BUFFER_BINDING);
    const states = [
      gl.UNPACK_ALIGNMENT,
      gl.UNPACK_FLIP_Y_WEBGL,
      gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL,
      gl.UNPACK_COLORSPACE_CONVERSION_WEBGL,
      gl.UNPACK_ROW_LENGTH,
      gl.UNPACK_IMAGE_HEIGHT,
      gl.UNPACK_SKIP_PIXELS,
      gl.UNPACK_SKIP_ROWS,
      gl.UNPACK_SKIP_IMAGES,
    ];
    const previous = states.map((x) => gl.getParameter(x));
    if (this.profiling) {
      this.endStage("state-save");
      this.beginStage("upload");
    }
    let texture;
    try {
      const start = performance.now();
      texture = gl.createTexture();
      if (!texture) throw new Error("Texture allocation failed");
      gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER, null);
      gl.bindTexture(gl.TEXTURE_2D, texture);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      states.forEach((x, i) => gl.pixelStorei(x, i === 0 ? 4 : 0));
      gl.texImage2D(
        gl.TEXTURE_2D,
        0,
        gl.RGBA,
        gl.RGBA,
        gl.UNSIGNED_BYTE,
        source.frame,
      );
      if (gl.getError() !== gl.NO_ERROR)
        throw new Error("WebGL upload rejected");
      this.metrics.uploadMs += performance.now() - start;
      this.metrics.uploads++;
      return {
        texture,
        width: source.width,
        height: source.height,
        rotation: source.rotation,
        flip: source.flip,
      };
    } catch (error) {
      if (texture) gl.deleteTexture(texture);
      this.error = "Media texture upload failed";
      return null;
    } finally {
      if (this.profiling) {
        this.endStage("upload");
        this.beginStage("state-restore");
      }
      states.forEach((x, i) => gl.pixelStorei(x, previous[i]));
      gl.bindBuffer(gl.PIXEL_UNPACK_BUFFER, unpackBuffer);
      gl.bindTexture(gl.TEXTURE_2D, binding);
      gl.activeTexture(active);
      source.frame.close();
      this.metrics.closedFrames++;
      // Resume the producer only after the surrounding Compose draw has unwound.
      if (!this.error && source.onConsumed) queueMicrotask(source.onConsumed);
      if (this.profiling) this.endStage("state-restore");
    }
  }
  deleteUnadopted(texture) {
    this.gl.deleteTexture(texture);
  }
  close() {
    this.closed = true;
    this.releasePending();
    this.gl?.canvas.removeEventListener("webglcontextlost", this.contextLost);
  }
}
