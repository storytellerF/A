package com.storyteller_f.a.app.compose_app.pages.topic

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.compose_app.pages.community.CommunityRefCell
import com.storyteller_f.a.app.compose_app.pages.room.RoomRefCell
import com.storyteller_f.a.app.compose_app.pages.user.UserRefCell
import com.storyteller_f.shared.type.toPrimaryKeyOrNull

class TopicRoute(
    val pattern: String,
    val builder: @Composable (Map<String, String>) -> Unit
) {
    companion object {
        fun parseRefUri(
            string: String
        ): Pair<@Composable ((
            Map<String, String>,
        ) -> Unit)?, MutableMap<String, String>> {
            val target = string.split("/")
            val map = mutableMapOf<String, String>()
            val matched = ROUTE.firstOrNull {
                val pattern = it.pattern.split("/")
                if (target.size != pattern.size) {
                    false
                } else {
                    var i = 0
                    while (true) {
                        if (i >= target.size) break
                        val e = pattern[i]
                        if (e.startsWith("{") && e.endsWith("}")) {
                            map[e.removeSurrounding("{", "}")] = target[i]
                        } else {
                            if (e != target[i]) {
                                break
                            }
                        }
                        i++
                    }
                    i == target.size
                }
            }
            return matched?.builder to map
        }
    }
}

val ROUTE = mutableListOf(
    TopicRoute("/topic/{id}") { params ->
        params["id"]?.toPrimaryKeyOrNull()?.let {
            TopicRefCell(it)
        }
    },
    TopicRoute("/topic/a/{aid}") { params ->
        params["aid"]?.let {
            TopicRefCell(it)
        }
    },
    TopicRoute("/room/{id}") { params ->
        params["id"]?.toPrimaryKeyOrNull()?.let {
            RoomRefCell(it)
        }
    },
    TopicRoute("/room/a/{aid}") { params ->
        params["aid"]?.let {
            RoomRefCell(it)
        }
    },
    TopicRoute("/community/{id}") { params ->
        params["id"]?.toPrimaryKeyOrNull()?.let {
            CommunityRefCell(it)
        }
    },
    TopicRoute("/community/a/{aid}") { params ->
        params["aid"]?.let {
            CommunityRefCell(it)
        }
    },
    TopicRoute("/user/{id}") { params ->
        params["id"]?.toPrimaryKeyOrNull()?.let {
            UserRefCell(it)
        }
    },
    TopicRoute("/user/a/{aid}") { params ->
        params["aid"]?.let {
            UserRefCell(it)
        }
    }
)
