package com.storyteller_f.a.client.core

import com.storyteller_f.shared.model.PanelAccountInfo
import com.storyteller_f.shared.model.UserInfo
import io.github.aakira.napier.Napier
import io.ktor.client.request.*
import io.ktor.http.*

suspend fun <U> HttpRequestBuilder.addRequestHeaders(
    sessionModel: SessionModel<U>,
    passHolder: PassHolder,
    addRequestHeader: HttpRequestBuilder.(U, String) -> Unit
) {
    val passSession = passHolder.currentUserPass
    val session = sessionModel.dataAndSignature
    val userInfo = sessionModel.userHandler.data.value
    Napier.i(tag = "ClientAuth") {
        "addRequestHeaders session: $session"
    }
    if (session != null && passSession != null) {
        val (localData, localSignature) = session
        if (localData.isNotBlank() && !localSignature.isNullOrBlank()) {
            Napier.i {
                "addRequestHeaders headers $localData $localSignature"
            }
            if (userInfo == null) {
                val address = passSession.address().getOrThrow()
                headers[HttpHeaders.Authorization] =
                    """Custom ad="$address", sig="$localSignature""""
            } else {
                addRequestHeader(userInfo, localSignature)
            }
        }
    }
}

fun HttpRequestBuilder.addRequestHeadersFromInfo(userInfo: UserInfo, sig: String) {
    if (userInfo.aid.isNullOrBlank()) {
        val userId = userInfo.id
        headers[HttpHeaders.Authorization] =
            """Custom id="$userId", sig="$sig""""
    } else {
        headers[HttpHeaders.Authorization] =
            """Custom aid="${userInfo.aid}", sig="$sig""""
    }
}

fun HttpRequestBuilder.addRequestHeadersFromInfo(userInfo: PanelAccountInfo, sig: String) {
    val userId = userInfo.id
    headers[HttpHeaders.Authorization] =
        """Custom id="$userId", sig="$sig""""
}
