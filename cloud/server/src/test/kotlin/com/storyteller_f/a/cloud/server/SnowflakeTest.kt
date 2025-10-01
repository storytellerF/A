package com.storyteller_f.a.cloud.server

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.buildNameService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.pathString

class SnowflakeTest {
    @Test
    fun `test name`() {
        runBlocking {
            SnowflakeFactory.setMachine(0)
            val nameService = buildNameService(MergedEnv(emptyList()))
            println(nameService.parse(SnowflakeFactory.nextId()))
        }
    }

    @Test
    fun `test path`() {
        val get = Paths.get(".")
        println(get.pathString)
        println(get.toRealPath().pathString)
    }
}
