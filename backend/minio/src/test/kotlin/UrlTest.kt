import com.storyteller_f.a.backend.minio.replaceUrl
import kotlin.test.Test

class UrlTest {
    @Test
    fun `test url replace`() {
        val host = "https://test.com:9090"
        val url = "http://test.com:909/test?a=1&b=2"
        val newUrl = replaceUrl(host, url)
        assert(newUrl == "https://test.com:9090/test?a=1&b=2")
    }
}
