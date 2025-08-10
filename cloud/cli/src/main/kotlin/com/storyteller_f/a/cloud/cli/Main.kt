package com.storyteller_f.a.cloud.cli

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.exposed.buildExposedDatabase
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.MergedEnv
import com.storyteller_f.a.backend.service.databaseConnection
import com.storyteller_f.a.backend.service.mediaService
import com.storyteller_f.a.backend.service.naming.NameService
import com.storyteller_f.a.backend.service.object_storage.loadAvif
import com.storyteller_f.a.backend.service.readEnv
import com.storyteller_f.a.backend.service.topicDocumentService
import com.storyteller_f.shared.kmpLogger
import io.github.aakira.napier.Napier
import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli

lateinit var backend: Backend

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    Napier.base(kmpLogger)
    Napier.i(tag = "cli") {
        "args: ${args.contentToString()}"
    }
    loadAvif()
    SnowflakeFactory.setMachine(0)
    backend = buildBackendFromEnv(readEnv())
    val argParser = ArgParser("ACli")
    argParser.subcommands(AddPreset(), CleanCommand(), PrintCommand(), InitTableCommand())
    argParser.parse(args)
}

fun buildBackendFromEnv(env: MergedEnv): Backend {
    println("load env: ${env["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(env)

    val buildType = env["BUILD_TYPE"] ?: "prod"
    val flavor = env["FLAVOR"] ?: throw Exception("FLAVOR is empty")

    val customConfig = CustomConfig(buildType, flavor, null)

    val topicDocumentService = topicDocumentService(env)
    val mediaService = mediaService(env)

    val exposedDatabase = buildExposedDatabase(databaseConnection)
    return Backend(
        customConfig,
        topicDocumentService,
        mediaService,
        NameService(),
        exposedDatabase
    )
}
