/*
 * This is a private project. All rights reserved.
 */

@file:OptIn(ExperimentalWasmJsInterop::class)

package org.jetbrains.skiko

import org.jetbrains.skia.DirectContext
import org.jetbrains.skia.impl.NativePointer
import kotlin.js.JsAny

internal val mediaContexts = mutableMapOf<NativePointer, WebGLMediaContext>()

@JsFun("gl => gl.currentContext?.handle ?? 0")
private external fun currentHandle(gl: GLInterface): NativePointer

@JsFun("gl => gl.currentContext.GLctx")
private external fun currentContext(gl: GLInterface): JsAny

/** A borrowed renderer identity. Its use scope restores the previous Emscripten context. */
@ExperimentalSkikoApi
class WebGLMediaContext internal constructor(
    private val handle: NativePointer,
    private val context: DirectContext,
) {
    var isDisposed: Boolean = false
        internal set

    /** Uses the already-active renderer without redundant Emscripten context activation. */
    fun <T> useCurrent(block: (JsAny, DirectContext) -> T): T {
        check(!isDisposed && currentHandle(GL) == handle) { "Renderer context is not current" }
        return block(currentContext(GL), context)
    }

    fun <T> use(block: (JsAny, DirectContext) -> T): T {
        check(!isDisposed) { "Renderer context is disposed" }
        val previous = currentHandle(GL)
        check(GL.makeContextCurrent(handle)) { "Cannot activate renderer context" }
        return try {
            block(currentContext(GL), context)
        } finally {
            GL.makeContextCurrent(previous)
        }
    }
}

/** Valid only for synchronous rendering; never retain or close the borrowed DirectContext. */
@ExperimentalSkikoApi
fun currentWebGLMediaContext(): WebGLMediaContext =
    checkNotNull(mediaContexts[currentHandle(GL)]) { "No active Skiko renderer context" }

@JsFun("(gl, texture) => { const id = gl.getNewId(gl.textures); texture.name = id; gl.textures[id] = texture; return id; }")
private external fun registerTexture(gl: GLInterface, texture: JsAny): Int

/** Transfer the returned id to Image.adoptTextureFrom, or unregister it on failure. */
@ExperimentalSkikoApi
fun pushTexture(texture: JsAny): Int = registerTexture(GL, texture)

@JsFun("(gl, id) => { if (gl.textures[id]) gl.textures[id].name = 0; gl.textures[id] = null; }")
private external fun removeTexture(gl: GLInterface, id: Int)

@ExperimentalSkikoApi
fun unregisterTexture(id: Int) = removeTexture(GL, id)
