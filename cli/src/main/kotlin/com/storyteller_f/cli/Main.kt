package com.storyteller_f.cli

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.Backend
import com.storyteller_f.buildBackendFromEnv
import com.storyteller_f.readEnv
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.github.aakira.napier.log
import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli

lateinit var backend: Backend

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    SnowflakeFactory.setMachine(0)
    backend = buildBackendFromEnv(readEnv())
    Napier.base(DebugAntilog())
    log {
        args.contentToString()
    }
    val argParser = ArgParser("ACli")
    argParser.subcommands(AddPreset(), CleanCommand(), PrintCommand())
    argParser.parse(args)
}
