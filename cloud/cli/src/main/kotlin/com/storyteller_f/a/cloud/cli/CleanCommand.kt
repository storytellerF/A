package com.storyteller_f.a.cloud.cli

import com.storyteller_f.shared.model.A_FILE_DEFAULT_BUCKET
import io.github.aakira.napier.Napier
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.Subcommand
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalCli::class)
class CleanCommand : Subcommand("clean", "clean all data") {
    override fun execute() {
        runBlocking {
            val connected = requireBackend()
            connected.database.clean()
            Napier.i {
                "database tables delete done"
            }
            connected.objectStorageService.clean(A_FILE_DEFAULT_BUCKET).getOrThrow()
            connected.topicSearchService.clean().getOrThrow()
            connected.userSearchService.clean().getOrThrow()
            connected.roomSearchService.clean().getOrThrow()
            connected.communitySearchService.clean().getOrThrow()
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
            requireBackend().database.init()
        }
        Napier.i {
            "init done"
        }
    }
}

@OptIn(ExperimentalCli::class)
class PrintCommand : Subcommand("print", "print") {
    override fun execute() = Unit
}
