package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.backend.core.getImageDimension
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.copy
import com.storyteller_f.a.client.core.extractAlbum
import com.storyteller_f.a.client.core.getMediaList
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.a.cloud.core.service.getExtensionFromMimeType
import com.storyteller_f.a.cloud.core.utils.readFlacAlbumFromAudioStream
import com.storyteller_f.shared.obj.ObjectTuple
import com.storyteller_f.shared.type.ObjectType
import io.ktor.http.ContentType
import io.ktor.http.defaultForFileExtension
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import org.bytedeco.opencv.global.opencv_core
import org.bytedeco.opencv.global.opencv_imgcodecs
import org.bytedeco.opencv.global.opencv_imgproc
import org.bytedeco.opencv.opencv_core.Mat
import org.bytedeco.opencv.opencv_core.Scalar
import org.bytedeco.opencv.opencv_core.Size
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.io.path.Path
import kotlin.math.log10
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MediaTest {
    @Test
    fun `test upload media`() {
        test {
            val firstTuple = attachSession {
                val response =
                    upload(
                        ObjectTuple(it.uid, ObjectType.USER),
                        getUploadDataFromText("hello")
                    ).getOrThrow()
                assertEquals("${it.uid}/hello.txt", response.data.first().fullName)
                val mediaList = getMediaList(it.uid, ObjectType.USER, null, 10)
                assertListSize(1, mediaList)
                mediaList.getOrThrow().data.first()
            }
            attachSession {
                val response = copy(firstTuple.custom.id).getOrThrow()
                assertEquals("${it.uid}/hello.txt", response.data.first().fullName)
                assertListSize(1, getMediaList(it.uid, ObjectType.USER, null, 10))
            }
        }
    }

    @Test
    fun `get png size`() {
        runTest {
            val dimension = getImageDimension("avatar1.png", "image/png") {
                ClassLoader.getSystemResourceAsStream("avatar1.png")!!
            }
            assertNotNull(dimension)
            assertEquals(dimension.width, 420)
            assertEquals(dimension.height, 420)
        }
    }

    @Test
    fun `extract audio album`() {
        val vendor = System.getProperty("java.vendor") // 厂商
        val version = System.getProperty("java.version") // 版本
        val runtime = System.getProperty("java.runtime.name")

        println("当前JDK: vendor=$vendor, version=$version, runtime=$runtime")
        // https://github.com/bytedeco/javacpp-presets/issues/1649
        if (vendor.contains("OpenJDK", ignoreCase = true)) {
            error("❌ 不允许使用 OpenJDK")
        }
        ClassLoader.getSystemResourceAsStream("I_Do_not_Wanna_Live_Forever.flac")
            ?.use {
                it.readFlacAlbumFromAudioStream { image, mimeType ->
                    val name = "build/test/cover.${getExtensionFromMimeType(mimeType)}"
                    FileOutputStream(name).use { output ->
                        output.write(image)
                    }
                }
            } ?: throw Exception("flac is not exists")
        ClassLoader.getSystemResourceAsStream("cover.jpg")?.use {
            Files.copy(it, Path("build/test/cover_origin.jpg"), StandardCopyOption.REPLACE_EXISTING)
        } ?: throw Exception("cover is not exists")
        val img1 = opencv_imgcodecs.imread("build/test/cover.jpg")
        if (img1.empty()) {
            error("图像加载失败！")
        }
        val img2 = opencv_imgcodecs.imread("build/test/cover_origin.jpg")
        if (img2.empty()) {
            error("图像加载失败！")
        }
        val psnr = getPSNR(img1, img2)
        val mssim = getMSSIM(img1, img2)!!
        assertEquals(0.0, psnr, 0.00001)
        assertEquals(0.999999, mssim.get(0), 0.00001)
        assertEquals(0.999999, mssim.get(1), 0.00001)
        assertEquals(0.999999, mssim.get(2), 0.00001)
    }

    @Test
    fun `test audio album`() = test(
        mapOf("FILE_SYSTEM_MEDIA_PATH" to "build/test/a_file")
    ) {
        attachSession {
            val name = "I_Do_not_Wanna_Live_Forever.flac"
            val data = getUploadDataFromResources(name)
            val response = upload(
                ObjectTuple(it.uid, ObjectType.USER),
                data
            ).getOrThrow()
            extractAlbum(response.data.first().id).getOrThrow()
        }
    }
}

private fun getUploadDataFromResources(name: String): UploadData {
    val inputStream =
        ClassLoader.getSystemResourceAsStream(name)!!
    val bytes = inputStream.readBytes()
    val data = UploadData(
        bytes.size.toLong(),
        name,
        ContentType.defaultForFileExtension("flac")
    ) {
        Buffer().apply {
            write(bytes)
        }
    }
    return data
}

private fun getPSNR(mat1: Mat, mat2: Mat): Double {
    val s1 = Mat()
    opencv_core.absdiff(mat1, mat2, s1) // |I1 - I2|
    s1.convertTo(s1, opencv_core.CV_32F) // cannot make a square on 8 bits
    val s2 = s1.mul(s1).asMat() // |I1 - I2|^2

    val s = opencv_core.sumElems(s2) // sum elements per channel

    val sse = s.get(0) + s.get(1) + s.get(2) // sum channels

    if (sse <= 1e-10) { // for small values return zero
        return 0.0
    } else {
        val mse = sse / (mat1.channels() * mat1.total()).toDouble()
        return 10.0 * log10((255 * 255) / mse)
    }
}

private fun getMSSIM(i1: Mat, i2: Mat): Scalar? {
    val constant1 = 6.5025
    val constant2 = 58.5225

    /***************************** INITS  */
    val dataType: Int = opencv_core.CV_32F
    val matrix1 = Mat()
    val matrix2 = Mat()
    i1.convertTo(matrix1, dataType) // cannot calculate on one byte large values
    i2.convertTo(matrix2, dataType)
    val matrix2Squared = matrix2.mul(matrix2).asMat() // I2^2
    val matrix1Squared = matrix1.mul(matrix1).asMat() // I1^2
    val matrix1Matrix2 = matrix1.mul(matrix2).asMat() // I1 * I2

    /*************************** END INITS  */
    // PRELIMINARY COMPUTING
    val mean1 = Mat()
    val mean2 = Mat()
    opencv_imgproc.GaussianBlur(matrix1, mean1, Size(11, 11), 1.5)
    opencv_imgproc.GaussianBlur(matrix2, mean2, Size(11, 11), 1.5)
    val mean1Squared = mean1.mul(mean1).asMat()
    val mean2Squared = mean2.mul(mean2).asMat()
    val mean1Mean2 = mean1.mul(mean2).asMat()
    val sigma1Squared = Mat()
    val sigma2Squared = Mat()
    val sigma12 = Mat()
    opencv_imgproc.GaussianBlur(matrix1Squared, sigma1Squared, Size(11, 11), 1.5)
    val sigma1Squared1 = opencv_core.subtract(sigma1Squared, mean1Squared).asMat()
    opencv_imgproc.GaussianBlur(matrix2Squared, sigma2Squared, Size(11, 11), 1.5)
    val sigma2Squared1 = opencv_core.subtract(sigma2Squared, mean2Squared).asMat()
    opencv_imgproc.GaussianBlur(matrix1Matrix2, sigma12, Size(11, 11), 1.5)
    val sigma121 = opencv_core.subtract(sigma12, mean1Mean2).asMat()
    val temp1 =
        opencv_core.add(opencv_core.multiply(2.0, mean1Mean2), Scalar.all(constant1)).asMat()
    val temp2 = opencv_core.add(opencv_core.multiply(2.0, sigma121), Scalar.all(constant2)).asMat()
    val temp3 = temp1.mul(temp2).asMat() // t3 = ((2*mu1_mu2 + C1).*(2*sigma12 + C2))
    val temp1Final =
        opencv_core.add(opencv_core.add(mean1Squared, mean2Squared), Scalar.all(constant1)).asMat()
    val temp2Final =
        opencv_core.add(opencv_core.add(sigma1Squared1, sigma2Squared1), Scalar.all(constant2))
            .asMat()
    val temp1Result =
        temp1Final.mul(temp2Final).asMat() // t1 =((mu1_2 + mu2_2 + C1).*(sigma1_2 + sigma2_2 + C2))
    val ssimMap = Mat()
    opencv_core.divide(temp3, temp1Result, ssimMap) // ssim_map =  t3./t1;
    return opencv_core.mean(ssimMap)
}
