package com.storyteller_f

import com.perraco.utils.SnowflakeFactory
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
        args.joinToString(",")
    }
    val argParser = ArgParser("ACli")
    argParser.subcommands(Add(), CleanCommand())
    argParser.parse(args)
}
