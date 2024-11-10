package com.storyteller_f.a.app.topic

import androidx.compose.runtime.Composable
import com.storyteller_f.a.app.community.CommunityRefCell
import com.storyteller_f.a.app.room.RoomRefCell
import com.storyteller_f.a.app.user.UserRefCell
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey

class TopicRoute(
    val pattern: String,
    val builder: @Composable (Map<String, String>, onClick: (PrimaryKey, ObjectType) -> Unit) -> Unit
) {
    companion object {
        fun parseRefUri(
            string: String
        ): Pair<@Composable ((
            Map<String, String>,
            (PrimaryKey, ObjectType) -> Unit
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
    TopicRoute("/topic/{id}") { params, onClick ->
        params["id"]?.toULongOrNull()?.let {
            TopicRefCell(it) {
                onClick(it, ObjectType.TOPIC)
            }
        }
    },
    TopicRoute("/topic/a/{aid}") { params, onClick ->
        params["aid"]?.let {
            TopicRefCell(it) {
                onClick(it, ObjectType.TOPIC)
            }
        }
    },
    TopicRoute("/room/{id}") { params, onClick ->
        params["id"]?.toULongOrNull()?.let {
            RoomRefCell(it) {
                onClick(it, ObjectType.ROOM)
            }
        }
    },
    TopicRoute("/room/a/{aid}") { params, onClick ->
        params["aid"]?.let {
            RoomRefCell(it) {
                onClick(it, ObjectType.ROOM)
            }
        }
    },
    TopicRoute("/community/{id}") { params, onClick ->
        params["id"]?.toULongOrNull()?.let {
            CommunityRefCell(it) {
                onClick(it, ObjectType.COMMUNITY)
            }
        }
    },
    TopicRoute("/community/a/{aid}") { params, onClick ->
        params["aid"]?.let {
            CommunityRefCell(it) {
                onClick(it, ObjectType.COMMUNITY)
            }
        }
    },
    TopicRoute("/user/{id}") { params, onClick ->
        params["id"]?.toULongOrNull()?.let {
            UserRefCell(it) {
                onClick(it, ObjectType.USER)
            }
        }
    },
    TopicRoute("/user/a/{aid}") { params, onClick ->
        params["aid"]?.let {
            UserRefCell(it) {
                onClick(it, ObjectType.USER)
            }
        }
    }
)
