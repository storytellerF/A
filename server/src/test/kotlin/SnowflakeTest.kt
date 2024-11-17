import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.naming.NameService
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SnowflakeTest {
    @Test
    fun `test name`() {
        runBlocking {
            SnowflakeFactory.setMachine(0)
            println(NameService().parse(SnowflakeFactory.nextId()))
        }
    }
}
