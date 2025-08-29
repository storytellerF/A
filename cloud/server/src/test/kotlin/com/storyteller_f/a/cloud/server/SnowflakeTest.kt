package com.storyteller_f.a.cloud.server

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.service.naming.NameService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.nio.file.Paths
import kotlin.io.path.pathString

class SnowflakeTest {
    @Test
    fun `test name`() {
        runBlocking {
            SnowflakeFactory.setMachine(0)
            println(NameService().parse(SnowflakeFactory.nextId()))
        }
    }

    @Test
    fun `test path`() {
        val get = Paths.get(".")
        println(get.pathString)
        println(get.toRealPath().pathString)
    }
}
