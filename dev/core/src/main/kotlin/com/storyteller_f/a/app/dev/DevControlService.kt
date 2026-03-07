package com.storyteller_f.a.app.dev

import kotlinx.rpc.annotations.Rpc

@Rpc
interface DevControlService {
    suspend fun startCloudServer(): String
    suspend fun stopCloudServer(): String
    suspend fun startCloudWorker(): String
    suspend fun stopCloudWorker(): String
    suspend fun shutdown(): String
}
