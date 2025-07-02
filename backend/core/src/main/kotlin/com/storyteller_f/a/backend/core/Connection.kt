package com.storyteller_f.a.backend.core

data class ElasticConnection(val url: String, val certFile: String, val name: String, val pass: String)

data class MinIoConnection(val url: String, val user: String, val pass: String)

data class DatabaseConnection(val uri: String, val driver: String, val user: String, val password: String)
