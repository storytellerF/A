/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.cloud.openpdf

import com.storyteller_f.result.CustomResult
import com.storyteller_f.result.getOrThrow
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.openpdf.text.pdf.BaseFont
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FontInstanceRepositoryTest {
    @Test
    fun `static faces bypass native instancing`() {
        val bytes = fontBytes("mono-regular.ttf")
        val repository = FontInstanceRepository(UnavailableInstancer())
        assertContentEquals(bytes, repository.getInstance(bytes).getOrThrow().copyBytes())
    }

    @Test
    fun `static faces take priority for each weight`() {
        val regular = fontBytes("mono-regular.ttf")
        val bold = fontBytes("mono-bold.ttf")
        val family = PdfFontFamily(regular, bold, byteArrayOf(1))
        val repository = FontInstanceRepository(UnavailableInstancer())
        assertContentEquals(regular, family.getInstance(repository, 400).getOrThrow().copyBytes())
        assertContentEquals(bold, family.getInstance(repository, 700).getOrThrow().copyBytes())
    }

    @Test
    fun `invalid selected static faces do not fall back`() {
        val variable = fontBytes("NotoSansMono[wdth,wght].ttf")
        val repository = FontInstanceRepository(UnavailableInstancer())
        for (invalid in listOf(byteArrayOf(1), variable)) {
            val family = PdfFontFamily(invalid, null, variable)
            assertIs<FontError.InvalidFont>(family.getInstance(repository, 400).errorOrNull())
        }
    }

    @Test
    fun `static cache identity uses font content`() {
        val regular = fontBytes("mono-regular.ttf")
        val repository = FontInstanceRepository(UnavailableInstancer())
        val first = repository.getInstance(regular).getOrThrow()
        assertSame(first, repository.getInstance(regular.copyOf()).getOrThrow())
        assertNotSame(first, repository.getInstance(fontBytes("mono-bold.ttf")).getOrThrow())
    }

    @Test
    fun `variable family supplies missing static weight`() {
        val regular = fontBytes("mono-regular.ttf")
        val variable = fontBytes("NotoSansMono[wdth,wght].ttf")
        val repository = FontInstanceRepository()
        val family = PdfFontFamily(regular, null, variable)
        assertContentEquals(regular, family.getInstance(repository, 400).getOrThrow().copyBytes())
        assertFalse(SfntFont(family.getInstance(repository, 700).getOrThrow().copyBytes(), 0).hasTable("fvar"))
    }

    @Test
    fun `invalid font requests return typed failures`() {
        val repository = FontInstanceRepository()
        val variable = fontBytes("NotoSansMono[wdth,wght].ttf")
        for (axis in listOf(mapOf("wght" to 999f), mapOf("wght" to Float.NaN), mapOf("nope" to 400f))) {
            assertIs<FontError.InvalidFont>(repository.getInstance(variable, axes = axis).errorOrNull())
        }
        assertIs<FontError.InvalidFont>(repository.getInstance(variable, faceIndex = 1).errorOrNull())
        assertIs<FontError.InvalidFont>(repository.getInstance(byteArrayOf(1, 2)).errorOrNull())
    }

    @Test
    fun `cache normalizes defaults and protects font bytes`() {
        val bytes = fontBytes("NotoSansMono[wdth,wght].ttf")
        val repository = FontInstanceRepository()
        val first = repository.getInstance(bytes, axes = mapOf("wght" to 400f)).getOrThrow()
        val second = repository.getInstance(bytes, axes = mapOf("wdth" to 100f, "wght" to 400f)).getOrThrow()
        assertSame(first, second)
        val copy = first.copyBytes()
        val original = copy.copyOf()
        copy.fill(0)
        assertContentEquals(original, first.copyBytes())
        assertNotSame(first, repository.getInstance(bytes, axes = mapOf("wght" to 700f)).getOrThrow())
    }

    @Test
    fun `width axis updates metrics and removes variations`() {
        val source = fontBytes("NotoSansMono[wdth,wght].ttf")
        val repository = FontInstanceRepository()
        val widths =
            listOf(75f, 100f).map { width ->
                val bytes =
                    repository.getInstance(source, axes = mapOf("wght" to 700f, "wdth" to width))
                        .getOrThrow().copyBytes()
                val metadata = SfntFont(bytes, 0)
                listOf("fvar", "gvar", "avar", "HVAR", "MVAR").forEach { assertFalse(metadata.hasTable(it), it) }
                assertEquals(700, readWeight(bytes))
                BaseFont.createFont("metric.ttf", BaseFont.IDENTITY_H, true, false, bytes, null).getWidth('M'.code)
            }
        assertTrue(widths[0] > 0 && widths[0] < widths[1], widths.toString())
    }

    private fun readWeight(bytes: ByteArray): Int {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val count = buffer.getShort(4).toInt() and 0xffff
        val record = (0 until count).map { 12 + it * 16 }.single { buffer.getInt(it) == encodeTag("OS/2") }
        return buffer.getShort(buffer.getInt(record + 8) + 4).toInt() and 0xffff
    }

    @Test
    fun `concurrent cache publication is complete`() =
        runTest {
        val bytes = fontBytes("NotoSansMono[wdth,wght].ttf")
        val repository = FontInstanceRepository()
        val instances =
            (1..8).map {
                async(Dispatchers.Default) { repository.getInstance(bytes, axes = mapOf("wght" to 400f)).getOrThrow() }
            }.awaitAll()
        instances.forEach { assertSame(instances.first(), it) }
        assertFalse(SfntFont(instances.first().copyBytes(), 0).hasTable("fvar"))
    }

    @Test
    fun `cancellation propagates without cache publication`() {
        val calls = AtomicInteger()
        val cancellation = CancellationException("cancel font operation")
        val instancer =
            object : FontInstancer {
                override val version = "test"
                override fun instantiate(
                    source: ByteArray,
                    faceIndex: Int,
                    axes: Map<String, Float>,
                ): CustomResult<ByteArray, FontError> {
                    calls.incrementAndGet()
                    throw cancellation
                }
            }
        val repository = FontInstanceRepository(instancer)
        repeat(2) {
            assertSame(
                cancellation,
                assertFailsWith<CancellationException> {
                    repository.getInstance(fontBytes("NotoSansMono[wdth,wght].ttf"))
                },
            )
        }
        assertEquals(2, calls.get())
    }

    @Test
    fun `native failure closes handles in reverse order`() {
        val variable = fontBytes("NotoSansMono[wdth,wght].ttf")
        for (failurePoint in 1..6) {
            val native = FailingNative(failurePoint)
            assertTrue(HarfBuzzFontInstancer(native).instantiate(variable, 0, mapOf("wght" to 400f)).isFailure)
            assertEquals(native.allocated.reversed(), native.closed)
        }
    }

    @Test
    fun `native failures are not cached`() {
        val calls = AtomicInteger()
        val output = fontBytes("mono-regular.ttf")
        val instancer =
            object : FontInstancer {
                override val version = "test"
                override fun instantiate(
                    source: ByteArray,
                    faceIndex: Int,
                    axes: Map<String, Float>,
                ): CustomResult<ByteArray, FontError> =
                    if (calls.incrementAndGet() == 1) {
                    CustomResult.failure(FontError.InstancingFailed(IllegalStateException("synthetic failure")))
                } else {
                    CustomResult.success(output)
                }
            }
        val repository = FontInstanceRepository(instancer)
        val source = fontBytes("NotoSansMono[wdth,wght].ttf")
        assertIs<FontError.InstancingFailed>(repository.getInstance(source).errorOrNull())
        assertContentEquals(output, repository.getInstance(source).getOrThrow().copyBytes())
        assertEquals(2, calls.get())
    }

    @Test
    fun `collection face cache entries are independent`() {
        val regular = fontBytes("mono-regular.ttf")
        val bold = fontBytes("mono-bold.ttf")
        val collection = createCollection(listOf(regular, bold))
        val repository = FontInstanceRepository()
        val first = repository.getInstance(collection, 0).getOrThrow()
        val second = repository.getInstance(collection, 1).getOrThrow()
        assertNotSame(first, second)
        assertFalse(first.copyBytes().contentEquals(second.copyBytes()))
        assertTrue(SfntFont(first.copyBytes(), 0).hasTable("glyf"))
        assertTrue(SfntFont(second.copyBytes(), 0).hasTable("glyf"))
    }

    @Test
    fun `native errors preserve fatal VM failures`() {
        val result = captureFontFailure<Unit> { throw UnsatisfiedLinkError("missing native") }
        assertIs<FontError.NativeUnavailable>(result.errorOrNull())
        assertFailsWith<OutOfMemoryError> { captureFontFailure<Unit> { throw OutOfMemoryError("synthetic fatal") } }
    }

    private fun createCollection(faces: List<ByteArray>): ByteArray {
        val result = ByteBuffer.allocate(12 + faces.size * 4 + faces.sumOf { it.size }).order(ByteOrder.BIG_ENDIAN)
        result.putInt(encodeTag("ttcf")).putInt(0x00010000).putInt(faces.size)
        val offsets = faces.runningFold(12 + faces.size * 4) { offset, face -> offset + face.size }.dropLast(1)
        offsets.forEach { result.putInt(it) }
        faces.forEachIndexed { index, bytes ->
            val face = ByteBuffer.wrap(bytes.copyOf()).order(ByteOrder.BIG_ENDIAN)
            val tableCount = face.getShort(4).toInt() and 0xffff
            repeat(tableCount) { table ->
                val position = 12 + table * 16 + 8
                face.putInt(position, face.getInt(position) + offsets[index])
            }
            result.put(face.array())
        }
        return result.array()
    }

    private class UnavailableInstancer : FontInstancer {
        override val version: String get() = error("Static fonts must not load native code")
        override fun instantiate(
            source: ByteArray,
            faceIndex: Int,
            axes: Map<String, Float>,
        ): CustomResult<ByteArray, FontError> = error("Static fonts must not call instancing")
    }

    private class FailingNative(private val failurePoint: Int) : HarfBuzzCalls {
        override val version = "test"
        val allocated = mutableListOf<Int>()
        val closed = mutableListOf<Int>()
        private var step = 0
        private fun allocate(): FontNativeHandle {
            check(++step != failurePoint) { "Synthetic allocation failure" }
            val id = step
            allocated.add(id)
            return FontNativeHandle(Pointer(id.toLong())) { closed.add(id) }
        }
        override fun createBlob(bytes: ByteArray): FontNativeHandle = allocate()
        override fun createHandle(name: String, destroy: String, vararg args: Any?): FontNativeHandle = allocate()
        override fun invokeInt(name: String, vararg args: Any?): Int = 1
        override fun invokeVoid(name: String, vararg args: Any?) = Unit
        override fun readBlob(blob: Pointer): ByteArray = error("Synthetic serialization failure")
    }
}

internal fun fontBytes(name: String): ByteArray =
    checkNotNull(
    FontInstanceRepositoryTest::class.java.getResourceAsStream("/fonts/$name"),
).buffered().use { it.readBytes() }
