package com.storyteller_f.a.cloud.cli

import com.storyteller_f.shared.model.AMEDIA_DEFAULT_BUCKET
import io.github.aakira.napier.Napier
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCli::class)
class CleanCommand : Subcommand("clean", "clean all data") {
    override fun execute() {
        runBlocking {
            backend.exposedDatabase.clean()
            Napier.i {
                "database tables delete done"
            }
            backend.mediaService.clean(AMEDIA_DEFAULT_BUCKET).getOrThrow()
            backend.topicSearchService.clean().getOrThrow()
        }
        Napier.i {
            "clean done"
        }
    }
}

@OptIn(ExperimentalCli::class)
class InitTableCommand : Subcommand("init", "init table data") {
    override fun execute() {
        runBlocking {
            backend.exposedDatabase.init()
        }
        Napier.i {
            "init done"
        }
    }
}

@OptIn(ExperimentalCli::class)
class PrintCommand : Subcommand("print", "print") {
    override fun execute() {
        runBlocking {
            val result = backend.topicSearchService.searchDocument().getOrThrow()
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
