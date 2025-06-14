package com.storyteller_f.cli

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.backend.service.Backend
import com.storyteller_f.backend.service.buildBackendFromEnv
import com.storyteller_f.backend.service.media.loadAvif
import com.storyteller_f.backend.service.readEnv
import com.storyteller_f.shared.kmpLogger
import io.github.aakira.napier.Napier
import io.github.aakira.napier.log
import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli

lateinit var backend: Backend

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    loadAvif()
    SnowflakeFactory.setMachine(0)
    backend = buildBackendFromEnv(readEnv())
    Napier.base(kmpLogger)
    log {
        args.contentToString()
    }
    val argParser = ArgParser("ACli")
    argParser.subcommands(AddPreset(), CleanCommand(), PrintCommand())
    argParser.parse(args)
}
