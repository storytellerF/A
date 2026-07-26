package com.storyteller_f.a.client.compose_core.utils

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

private var nextAppiumSemanticsId = 0L

fun Modifier.appiumSemantics(
    testTag: String? = null,
    description: String? = null,
    text: String? = null,
    input: Boolean = false,
    inputValue: String? = null,
    onInputValueChange: ((String) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    val id = remember { nextAppiumSemanticsId++ }
    DisposableEffect(id) {
        onDispose { AppiumHtmlSemantics.remove(id) }
    }
    SideEffect {
        AppiumHtmlSemantics.updateAction(id, onClick)
        if (input) {
            AppiumHtmlSemantics.updateInput(
                id = id,
                value = inputValue.orEmpty(),
                onValueChange = onInputValueChange,
            )
        }
    }
    var modifier = this
    if (testTag != null) {
        modifier = modifier.testTag(testTag)
    }
    if (description != null) {
        modifier = modifier.semantics { contentDescription = description }
    }
    modifier.onGloballyPositioned { coordinates ->
        val position = coordinates.positionInWindow()
        val size = coordinates.size
        AppiumHtmlSemantics.update(
            id = id,
            testTag = testTag,
            description = description,
            text = text,
            input = input,
            action = onClick != null,
            left = position.x,
            top = position.y,
            width = size.width.toFloat(),
            height = size.height.toFloat(),
        )
    }
}

internal expect object AppiumHtmlSemantics {
    fun update(
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
    )

    fun updateInput(id: Long, value: String, onValueChange: ((String) -> Unit)?)

    fun updateAction(id: Long, onClick: (() -> Unit)?)

    fun remove(id: Long)
}
