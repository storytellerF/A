package com.storyteller_f.a.app.user

import com.storyteller_f.a.app.client
import com.storyteller_f.a.app.common.SimpleViewModel
import com.storyteller_f.a.app.common.serviceCatching
import com.storyteller_f.a.client_lib.getUserInfo
import com.storyteller_f.a.client_lib.getUserInfoByAid
import com.storyteller_f.shared.model.UserInfo
import com.storyteller_f.shared.type.PrimaryKey
import io.ktor.client.HttpClient

class UserViewModel(private val requestInfo: suspend HttpClient.() -> UserInfo) : SimpleViewModel<UserInfo>() {
    constructor(userId: PrimaryKey) : this({
        getUserInfo(userId)
    })

    constructor(userAid: String) : this({
        getUserInfoByAid(userAid)
    })

    init {
        load()
    }

    override suspend fun loadInternal() {
        handler.request {
            serviceCatching {
                requestInfo(client)
            }
        }
    }
}
