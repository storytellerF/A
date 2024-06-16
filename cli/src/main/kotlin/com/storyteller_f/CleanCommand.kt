package com.storyteller_f

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
        backend.mediaService.clean("apic")
        runBlocking {
            backend.topicDocumentService.clean()
        }
        Napier.i {
            "clean done."
        }
    }
}
