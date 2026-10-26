/* This is a private project. All rights reserved. */
// Diagnostic-only sampler tracking. It records real draws that sample an imported
// video texture, separately from canvas capture notifications and decoded frames.
export async function installGpuDrawDiagnostics(
  page,
  { traceMarks = false } = {},
) {
  await page.addInitScript((traceMarks) => {
    globalThis.mediaGpuDraws = [];
    globalThis.mediaGpuBindingChecks = [];
    const states = new WeakMap(),
      programs = new WeakMap(),
      locations = new WeakMap(),
      textures = new WeakMap(),
      attachments = new WeakMap(),
      framebufferFrames = new WeakMap();
    let rafId = 0,
      currentRaf = null;
    const raf = requestAnimationFrame;
    window.requestAnimationFrame = (callback) =>
      raf.call(window, (t) => {
        const previous = currentRaf;
        currentRaf = ++rafId;
        try {
          return callback(t);
        } finally {
          currentRaf = previous;
        }
      });
    function state(gl) {
      let s = states.get(gl);
      if (!s) {
        s = {
          unit: 0,
          textures: new Map(),
          program: null,
          fbo: null,
          readFbo: null,
          verifiedDraws: 0,
        };
        states.set(gl, s);
      }
      return s;
    }
    const cls = WebGL2RenderingContext.prototype;
    const wrap = (name, before, after) => {
      const original = cls[name];
      cls[name] = function (...args) {
        before?.(this, args);
        const result = original.apply(this, args);
        after?.(this, args, result);
        return result;
      };
    };
    wrap(
      "activeTexture",
      (gl, [unit]) => (state(gl).unit = unit - gl.TEXTURE0),
    );
    wrap("bindTexture", (gl, [target, texture]) => {
      if (target === gl.TEXTURE_2D)
        state(gl).textures.set(state(gl).unit, texture);
    });
    wrap("deleteTexture", (gl, [texture]) => {
      for (const [unit, bound] of state(gl).textures) {
        if (bound === texture) state(gl).textures.set(unit, null);
      }
      if (texture) textures.delete(texture);
    });
    wrap("useProgram", (gl, [program]) => (state(gl).program = program));
    wrap("bindFramebuffer", (gl, [target, fbo]) => {
      // Emscripten may pass undefined for handle zero; WebGL treats it as null.
      if (target !== gl.READ_FRAMEBUFFER) state(gl).fbo = fbo ?? null;
      if (target !== gl.DRAW_FRAMEBUFFER) state(gl).readFbo = fbo ?? null;
    });
    const propagate = (s, frame) => {
      if (!s.fbo) return;
      framebufferFrames.set(s.fbo, frame);
      const texture = attachments.get(s.fbo);
      if (texture) textures.set(texture, { ...frame, derived: true });
    };
    wrap(
      "framebufferTexture2D",
      null,
      (gl, [target, attachment, , texture]) => {
        const s = state(gl),
          fbo = target === gl.READ_FRAMEBUFFER ? s.readFbo : s.fbo;
        if (fbo && attachment === gl.COLOR_ATTACHMENT0) {
          if (texture) attachments.set(fbo, texture);
          else attachments.delete(fbo);
        }
      },
    );
    wrap("clear", (gl, [mask]) => {
      const s = state(gl);
      if (s.fbo && mask & gl.COLOR_BUFFER_BIT) {
        framebufferFrames.delete(s.fbo);
        const texture = attachments.get(s.fbo);
        if (texture) textures.delete(texture);
      }
    });
    wrap("blitFramebuffer", null, (gl) => {
      const s = state(gl),
        frame = s.readFbo && framebufferFrames.get(s.readFbo);
      if (frame) {
        propagate(s, frame);
        globalThis.mediaGpuDraws.push({
          ...frame,
          derived: true,
          time: performance.now(),
          raf: currentRaf,
          canvasTarget: s.fbo === null,
        });
      }
    });
    wrap("linkProgram", null, (gl, [program]) => {
      const samplers = new Map(),
        count = gl.getProgramParameter(program, gl.ACTIVE_UNIFORMS);
      for (let i = 0; i < count; i++) {
        const uniform = gl.getActiveUniform(program, i);
        if (uniform?.type === gl.SAMPLER_2D)
          samplers.set(uniform.name.replace(/\[0\]$/, ""), 0);
      }
      programs.set(program, samplers);
    });
    wrap("getUniformLocation", null, (gl, [program, name], location) => {
      if (location)
        locations.set(location, { program, name: name.replace(/\[0\]$/, "") });
    });
    const uniform = (gl, [location, value]) => {
      const entry = locations.get(location);
      if (entry) {
        const samplers = programs.get(entry.program);
        if (samplers?.has(entry.name))
          samplers.set(
            entry.name,
            typeof value === "number" ? value : value[0],
          );
      }
    };
    wrap("uniform1i", uniform);
    wrap("uniform1iv", uniform);
    wrap("texImage2D", null, (gl, args) => {
      const frame = args.at(-1);
      const texture = state(gl).textures.get(state(gl).unit);
      if (!texture) return;
      textures.delete(texture);
      if (frame instanceof VideoFrame)
        textures.set(texture, {
          mediaTime: frame.timestamp / 1e6,
          width: frame.displayWidth,
          height: frame.displayHeight,
        });
    });
    const draw = (gl) => {
      const s = state(gl),
        samplers = programs.get(s.program);
      if (!samplers) return;
      for (const unit of samplers.values()) {
        const frame = textures.get(s.textures.get(unit));
        if (frame) {
          if (s.verifiedDraws++ < 6)
            globalThis.mediaGpuBindingChecks.push({
              trackedCanvasTarget: s.fbo === null,
              actualCanvasTarget:
                gl.getParameter(gl.DRAW_FRAMEBUFFER_BINDING) === null,
            });
          if (
            traceMarks &&
            s.fbo === null &&
            s.markedMediaTime !== frame.mediaTime
          ) {
            s.markedMediaTime = frame.mediaTime;
            performance.mark("media.video.draw:" + frame.mediaTime);
          }
          propagate(s, frame);
          globalThis.mediaGpuDraws.push({
            ...frame,
            time: performance.now(),
            raf: currentRaf,
            canvasTarget: s.fbo === null,
          });
        }
      }
    };
    for (const name of [
      "drawArrays",
      "drawElements",
      "drawArraysInstanced",
      "drawElementsInstanced",
    ])
      wrap(name, draw);
  }, traceMarks);
}
