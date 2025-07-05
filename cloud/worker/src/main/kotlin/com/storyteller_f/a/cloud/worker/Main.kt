package com.storyteller_f.a.cloud.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.CustomConfig
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.exposed.CombinedDatabase
import com.storyteller_f.a.backend.exposed.CommunityDatabase
import com.storyteller_f.a.backend.exposed.ContainerDatabase
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
import com.storyteller_f.a.backend.service.mediaService
import com.storyteller_f.a.backend.service.naming.NameService
import com.storyteller_f.a.backend.service.readEnv
import com.storyteller_f.a.backend.service.topicDocumentService
import com.storyteller_f.shared.kmpLogger
import com.storyteller_f.shared.model.TaskRecordType
import com.storyteller_f.shared.utils.mapResult
import com.storyteller_f.shared.utils.mapResultIfNotNull
import com.storyteller_f.shared.utils.now
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

fun main() {
    Napier.base(kmpLogger)
    val env = readEnv()
    Napier.i {
        "start worker at ${env["SERVER_PORT"]}"
    }
    val backend = buildBackendFromEnv(env)
    runBlocking {
        async {
            while (true) {
                Napier.i(tag = "task") {
                    "execute ${now()}"
                }
                backend.doAcgTask()
            }
        }
    }
}

private suspend fun Backend.doAcgTask() {
    getAcgTaskListFromTopics().mapResultIfNotNull { (acgList, userAcgMap, list) ->
        exposedDatabase.userDatabase.addAcgForUser(acgList, userAcgMap, list, SnowflakeFactory.nextId())
    }.onSuccess {
        delay(1000)
        Napier.i(tag = "task") {
            "task success $it"
        }
    }.onFailure {
        delay(1000)
        Napier.i(tag = "task", throwable = it) {
            "task failed"
        }
    }
}

private suspend fun Backend.getAcgTaskListFromTopics() =
    exposedDatabase.userDatabase.getLatestTaskRecord(TaskRecordType.TOPIC_ACG).mapResult {
        exposedDatabase.topicDatabase.getTopicList(it?.processedId ?: 0)
    }.mapResult { list ->
        if (list.isNotEmpty()) {
            val acgList = list.groupBy {
                it.author
            }.mapValues {
                it.value.count()
            }.toList()
            val uids = acgList.map {
                it.first
            }
            exposedDatabase.userDatabase.getUserAcgByIds(ObjectListFetch.IdListFetch(uids)).map { list ->
                list.associate {
                    it.first to it.second
                }
            }.map { userAcgMap ->
                Triple(acgList, userAcgMap, list)
            }
        } else {
            Result.success(null)
        }
    }

fun buildBackendFromEnv(env: MergedEnv): Backend {
    println("load env: ${env["COMPOSE_PROJECT_NAME"]}")

    val databaseConnection = databaseConnection(env)

    val buildType = env["BUILD_TYPE"] ?: "prod"
    val flavor = env["FLAVOR"] ?: throw Exception("FLAVOR is empty")

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
        object : CombinedDatabase<User> {
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
