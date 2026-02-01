package com.storyteller_f.a.cloud.pdf

/**
 * Data class representing signature information for table-based visible signature.
 */
data class SignatureInfo(
    val signee: String,
    val timestamp: String,
    val hint: String
)
