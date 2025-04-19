package com.storyteller_f.shared.type

enum class TitleType {
    REGULAR,
    JOIN,
}

enum class TitleStatus {
    OK,
    EXPIRED
}

class GPTOutput(val text: String)

class GPTModel(val key: String, val value: String)
