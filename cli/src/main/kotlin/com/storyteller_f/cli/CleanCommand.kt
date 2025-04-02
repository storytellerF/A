package com.storyteller_f.cli

import com.storyteller_f.DatabaseFactory
import com.storyteller_f.shared.model.AMEDIA_BUCKET
import io.github.aakira.napier.Napier
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCli::class)
class CleanCommand : Subcommand("clean", "clean all data") {
    override fun execute() {
        DatabaseFactory.connect(backend.config.databaseConnection)
        DatabaseFactory.clean()
        Napier.i {
            "database tables removed."
        }
        runBlocking {
            backend.mediaService.clean(AMEDIA_BUCKET).getOrThrow()
            backend.topicSearchService.clean().getOrThrow()
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
            val result = backend.topicSearchService.searchDocument(
                10
            ).getOrThrow()
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
