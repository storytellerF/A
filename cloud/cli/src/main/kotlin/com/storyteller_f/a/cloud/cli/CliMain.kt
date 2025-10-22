package com.storyteller_f.a.cloud.cli

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Backend
import com.storyteller_f.a.backend.core.CombinedDatabase
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.buildCommunitySearchService
import com.storyteller_f.a.backend.core.buildNameService
import com.storyteller_f.a.backend.core.buildRoomSearchService
import com.storyteller_f.a.backend.core.buildTopicSearchService
import com.storyteller_f.a.backend.core.buildUserSearchService
import com.storyteller_f.a.backend.core.databaseConnection
import com.storyteller_f.a.backend.core.loadAvif
import com.storyteller_f.a.backend.core.mediaService
import com.storyteller_f.a.backend.core.readEnv
import com.storyteller_f.a.backend.core.service.CommunitySearchService
import com.storyteller_f.a.backend.core.service.NameService
import com.storyteller_f.a.backend.core.service.ObjectStorageService
import com.storyteller_f.a.backend.core.service.RoomSearchService
import com.storyteller_f.a.backend.core.service.TopicSearchService
import com.storyteller_f.a.backend.core.service.UserSearchService
import com.storyteller_f.a.backend.exposed.buildExposedDatabase
import com.storyteller_f.shared.setupKmpLogger
import io.github.aakira.napier.Napier
import kotlinx.cli.ArgParser
import kotlinx.cli.ExperimentalCli
import java.io.File

lateinit var backend: Backend

@OptIn(ExperimentalCli::class)
fun main(args: Array<String>) {
    setupKmpLogger()
    Napier.i(tag = "cli") {
        "args: ${args.contentToString()}"
    }
    loadAvif()
    SnowflakeFactory.setMachine(0)
    val flavorFilePath = File("../../${BackendConfig.FLAVOR}.env").canonicalPath
    backend = buildBackendFromEnv(readEnv(flavorFilePath = flavorFilePath))
    val argParser = ArgParser("ACli")
    argParser.subcommands(AddPreset(), CleanCommand(), PrintCommand(), InitTableCommand())
    argParser.parse(args)
}

class CliBackend(
    override val customConfig: CustomConfig,
    override val topicSearchService: TopicSearchService,
    override val roomSearchService: RoomSearchService,
    override val communitySearchService: CommunitySearchService,
    override val userSearchService: UserSearchService,
    override val objectStorageService: ObjectStorageService,
    override val nameService: NameService,
    override val combinedDatabase: CombinedDatabase
) : Backend

fun buildBackendFromEnv(env: MergedEnv): Backend {
    Napier.i("load env: ${env["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(env)

    val buildType = env["BUILD_TYPE"] ?: "prod"
    val flavor = env["FLAVOR"] ?: throw Exception("FLAVOR is empty")

    val customConfig = CustomConfig(buildType, flavor, null)

    val topicSearchService = buildTopicSearchService(env)
    val userSearchService = buildUserSearchService(env)
    val roomSearchService = buildRoomSearchService(env)
    val communitySearchService = buildCommunitySearchService(env)
    val mediaService = mediaService(env)

    val exposedDatabase = buildExposedDatabase(databaseConnection)
    return CliBackend(
        customConfig,
        topicSearchService,
        roomSearchService,
        communitySearchService,
        userSearchService,
        mediaService,
        buildNameService(env),
        exposedDatabase
    )
}
