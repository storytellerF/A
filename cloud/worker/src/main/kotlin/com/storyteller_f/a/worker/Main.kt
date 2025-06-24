package com.storyteller_f.a.worker

import com.perraco.utils.SnowflakeFactory
import com.storyteller_f.a.backend.core.Config
import com.storyteller_f.a.backend.core.ObjectListFetch
import com.storyteller_f.a.backend.service.Backend
import com.storyteller_f.a.backend.service.MergedEnv
import com.storyteller_f.a.backend.service.databaseConnection
import com.storyteller_f.a.backend.service.mediaService
import com.storyteller_f.a.backend.service.naming.NameService
import com.storyteller_f.a.backend.service.readEnv
import com.storyteller_f.a.backend.service.topicDocumentService
import com.storyteller_f.a.exposed.CommunityDatabase
import com.storyteller_f.a.exposed.Database
import com.storyteller_f.a.exposed.ExposedCommunityDatabase
import com.storyteller_f.a.exposed.ExposedDatabaseFactory
import com.storyteller_f.a.exposed.ExposedDatabaseSession
import com.storyteller_f.a.exposed.ExposedRoomDatabase
import com.storyteller_f.a.exposed.ExposedTitleDatabase
import com.storyteller_f.a.exposed.ExposedTopicDatabase
import com.storyteller_f.a.exposed.ExposedUserDatabase
import com.storyteller_f.a.exposed.RoomDatabase
import com.storyteller_f.a.exposed.TitleDatabase
import com.storyteller_f.a.exposed.TopicDatabase
import com.storyteller_f.a.exposed.UserDatabase
import com.storyteller_f.a.exposed.tables.User
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

    val buildType = env["BUILD_TYPE"]
    val flavor = env["FLAVOR"]

    val config = Config(databaseConnection, buildType, flavor)

    val topicDocumentService = topicDocumentService(env)
    val mediaService = mediaService(env)

    val database = ExposedDatabaseFactory.connect(databaseConnection)
    val databaseSession = ExposedDatabaseSession(database, buildType)
    return Backend(
        config,
        env["SNAPSHOT_KEYSTORE_PATH"] to env["SNAPSHOT_KEY_PASS"],
        topicDocumentService,
        mediaService,
        NameService(),
        database,
        databaseSession,
        object : Database<User> {
            override val userDatabase: UserDatabase<User>
                get() = ExposedUserDatabase(databaseSession)
            override val topicDatabase: TopicDatabase
                get() = ExposedTopicDatabase(databaseSession, userDatabase)
            override val titleDatabase: TitleDatabase
                get() = ExposedTitleDatabase(databaseSession)
            override val communityDatabase: CommunityDatabase
                get() = ExposedCommunityDatabase(databaseSession, userDatabase)
            override val roomData: RoomDatabase
                get() = ExposedRoomDatabase(databaseSession, userDatabase)
        }
    )
}
