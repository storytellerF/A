import com.caoccao.javet.interop.NodeRuntime
import com.caoccao.javet.interop.V8Host
import com.caoccao.javet.node.modules.NodeModuleModule
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.use

class ParseTest : PlatformHeadlessTest() {
    @Test
    fun `test parse ascii doc`() {
        val scriptDir = File("src/headlessTest/resources")
        if (!File(scriptDir, "node_modules").exists()) {
            return
        }
        val scripts = File(scriptDir, "parse-ascii.js").bufferedReader().use {
            it.readText()
        }
        (V8Host.getNodeInstance().createV8Runtime() as NodeRuntime).use {
            it.getNodeModule(NodeModuleModule::class.java)
                .setRequireRootDirectory(scriptDir)

            val result = it.getExecutor(scripts)
                .executeString()
            assertEquals(
                """
                <div class="paragraph">
                <p>Hello, <em>Asciidoctor</em></p>
                </div>
            """.trimIndent(), result
            )
        }
    }
}