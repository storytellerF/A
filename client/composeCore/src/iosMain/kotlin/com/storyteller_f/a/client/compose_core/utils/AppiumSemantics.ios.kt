package com.storyteller_f.a.client.compose_core.utils

internal actual object AppiumHtmlSemantics {
    actual fun update(
        id: Long,
        testTag: String?,
        description: String?,
        text: String?,
        input: Boolean,
        action: Boolean,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ) = Unit

    actual fun updateInput(id: Long, value: String, onValueChange: ((String) -> Unit)?) = Unit

    actual fun updateAction(id: Long, onClick: (() -> Unit)?) = Unit

    actual fun remove(id: Long) = Unit
}
