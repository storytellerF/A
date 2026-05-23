package com.storyteller_f.a.app.dev

import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable

@Serializable
data class ServiceStatus(
    val serverRunning: Boolean,
    val workerRunning: Boolean
)

@Rpc
interface DevControlService {
    suspend fun startCloudServer(): String
    suspend fun stopCloudServer(): String
    suspend fun startCloudWorker(): String
    suspend fun stopCloudWorker(): String
    suspend fun shutdown(): String
    suspend fun getStatus(): ServiceStatus
}
