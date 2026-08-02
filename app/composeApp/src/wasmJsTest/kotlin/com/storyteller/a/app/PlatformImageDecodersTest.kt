/*
 * This is a private project. All rights reserved.
 */

package com.storyteller.a.app

import coil3.PlatformContext
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.storyteller_f.a.app.getAsyncImageLoader
import kotlinx.coroutines.test.runTest
import okio.Buffer
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class PlatformImageDecodersTest {
    @Test
    internal fun recognizesAvifMajorBrand() {
        val source = Buffer().write(byteArrayOf(0, 0, 0, ISO_BOX_SIZE)).writeUtf8("ftypavifmif1avif")

        assertTrue(source.hasAvifHeader())
    }

    @Test
    internal fun rejectsOtherIsoMediaBrands() {
        val source = Buffer().write(byteArrayOf(0, 0, 0, ISO_BOX_SIZE)).writeUtf8("ftypmp42mp42isom")

        assertFalse(source.hasAvifHeader())
    }

    @Test
    internal fun decodesAvifWithBrowserImageDecoder() = runTest { verifyAvifDecode() }

    private suspend fun verifyAvifDecode() {
        val context = PlatformContext.INSTANCE
        val imageLoader = getAsyncImageLoader(context)
        try {
            val request =
                ImageRequest.Builder(context)
                    .data(Base64.decode(TWO_PIXEL_AVIF))
                    .build()

            val result = imageLoader.execute(request)
            if (result is ErrorResult) throw result.throwable
            val successResult = assertIs<SuccessResult>(result)

            assertEquals(2, successResult.image.width)
            assertEquals(2, successResult.image.height)
        } finally {
            imageLoader.shutdown()
        }
    }

    private companion object {
        const val ISO_BOX_SIZE: Byte = 24
        const val TWO_PIXEL_AVIF =
            "AAAAIGZ0eXBhdmlmAAAAAGF2aWZtaWYxbWlhZk1BMUEAAADybWV0YQAAAAAAAAAoaGRscgAAAAAAAAAAcGljdAAAAAAAAAAAAAAA" +
                "AGxpYmF2aWYAAAAADnBpdG0AAAAAAAEAAAAeaWxvYwAAAABEAAABAAEAAAABAAABGgAAABkAAAAoaWluZgAAAAAAAQAAABppbmZl" +
                "AgAAAAABAABhdjAxQ29sb3IAAAAAamlwcnAAAABLaXBjbwAAABRpc3BlAAAAAAAAAAIAAAACAAAAEHBpeGkAAAAAAwgICAAAAAxh" +
                "djFDgSAAAAAAABNjb2xybmNseAABAAIABoAAAAAXaXBtYQAAAAAAAAABAAEEAQKDBAAAACFtZGF0EgAKBzgANhAQIGkyDB+QP///" +
                "xAAArLK+CQ=="
    }
}
