package com.storyteller_f.a.cloud.cli

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.exposed.CommunityDatabase
import com.storyteller_f.a.backend.exposed.ContainerDatabase
import com.storyteller_f.a.backend.exposed.Database
import com.storyteller_f.a.backend.exposed.ExposedCommunityDatabase
import com.storyteller_f.a.backend.exposed.ExposedContainerDatabase
import com.storyteller_f.a.backend.exposed.ExposedDatabaseFactory
import com.storyteller_f.a.backend.exposed.ExposedDatabaseSession
import com.storyteller_f.a.backend.exposed.ExposedMediaDatabase
import com.storyteller_f.a.backend.exposed.ExposedRoomDatabase
import com.storyteller_f.a.backend.exposed.ExposedTitleDatabase
import com.storyteller_f.a.backend.exposed.ExposedTopicDatabase
import com.storyteller_f.a.backend.exposed.ExposedUserDatabase
import com.storyteller_f.a.backend.exposed.MediaDatabase
import com.storyteller_f.a.backend.exposed.RoomDatabase
import com.storyteller_f.a.backend.exposed.TitleDatabase
import com.storyteller_f.a.backend.exposed.TopicDatabase
import com.storyteller_f.a.backend.exposed.UserDatabase
import com.storyteller_f.a.backend.exposed.tables.User
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.MergedEnv
import com.storyteller_f.a.backend.service.databaseConnection
import com.storyteller_f.a.backend.service.media.loadAvif
import com.storyteller_f.a.backend.service.mediaService
import com.storyteller_f.a.backend.service.naming.NameService
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

    val buildType = env["BUILD_TYPE"]
    val flavor = env["FLAVOR"]

    val customConfig = CustomConfig(buildType, flavor, null)

    val topicDocumentService = topicDocumentService(env)
    val mediaService = mediaService(env)

    val database = ExposedDatabaseFactory.connect(databaseConnection)
    val databaseSession = ExposedDatabaseSession(database, null)
    return Backend(
        customConfig,
        topicDocumentService,
        mediaService,
        NameService(),
        database,
        databaseSession,
        object : Database<User> {
            override val userDatabase: UserDatabase<User>
                get() = ExposedUserDatabase(databaseSession)
            override val topicDatabase: TopicDatabase
                get() = ExposedTopicDatabase(databaseSession, containerDatabase)
            override val titleDatabase: TitleDatabase
                get() = ExposedTitleDatabase(databaseSession)
            override val communityDatabase: CommunityDatabase
                get() = ExposedCommunityDatabase(databaseSession, containerDatabase)
            override val roomData: RoomDatabase
                get() = ExposedRoomDatabase(databaseSession, containerDatabase)
            override val mediaDatabase: MediaDatabase
                get() = ExposedMediaDatabase(databaseSession)
            override val containerDatabase: ContainerDatabase
                get() = ExposedContainerDatabase(databaseSession)
        }
    )
}
