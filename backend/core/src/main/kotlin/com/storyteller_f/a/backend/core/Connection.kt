package com.storyteller_f.a.backend.core

data class ElasticConnection(
    val url: String,
    val certFile: String,
    val name: String,
    val pass: String,
    val refresh: Boolean = true
)

data class MinIoConnection(val url: String, val user: String, val pass: String)

data class DatabaseConnection(
    val uri: String,
    val driver: String,
    val user: String,
    val password: String
)

object ContainerImages {
    const val ELASTICSEARCH = "docker.elastic.co/elasticsearch/elasticsearch:8.17.0"
    const val MINIO = "minio/minio:RELEASE.2024-12-18T13-15-44Z"
    const val POSTGRESQL = "pgvector/pgvector:pg16"
}
