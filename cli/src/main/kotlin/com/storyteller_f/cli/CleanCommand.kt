package com.storyteller_f.cli

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.shared.type.ObjectType
import io.github.aakira.napier.Napier
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCli::class)
class CleanCommand : Subcommand("clean", "clean all data") {
    override fun execute() {
        DatabaseFactory.clean(backend.config.databaseConnection)
        Napier.i {
            "database tables removed."
        }
        backend.mediaService.clean("amedia")
        runBlocking {
            backend.topicSearchService.clean()
        }
        Napier.i {
            "clean done."
        }
    }
}

@OptIn(ExperimentalCli::class)
class PrintCommand : Subcommand("print", "print") {
    override fun execute() {
        runBlocking {
            val result = backend.topicSearchService.searchDocument(10, parent = null to ObjectType.COMMUNITY).getOrThrow()
            Napier.i {
                "total ${result.total} ${result.list.size}"
            }
            result.list.forEach {
                Napier.i {
                    it.toString()
                }
            }
        }
    }
}