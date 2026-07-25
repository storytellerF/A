package com.storyteller_f.a.client.compose_core.utils

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLTextAreaElement

internal actual object AppiumHtmlSemantics {
    private const val OVERLAY_ID = "appium-html-semantics"
    private val elements = mutableMapOf<Long, HTMLElement>()
    private val inputCallbacks = mutableMapOf<Long, (String) -> Unit>()
    private val inputValues = mutableMapOf<Long, String>()
    private val actionCallbacks = mutableMapOf<Long, () -> Unit>()

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
    ) {
        if (!isEnabled()) return
        val element = elements.getOrPut(id) { createElement(id, input) }
        element.setAttribute("data-appium-semantics", id.toString())
        element.setOrRemoveAttribute("data-testid", testTag)
        element.setOrRemoveAttribute("aria-label", description)
        element.setOrRemoveAttribute("data-appium-text", text)
        element.updateInputAttributes(id, input)
        element.setBooleanAttribute("data-appium-action", action)
        element.updateLayout(input, action, left, top, width, height)
        element.updateAttachment(isInViewport(left, top, width, height))
    }

    private fun createElement(id: Long, input: Boolean): HTMLElement {
        return if (input) {
            (document.createElement("textarea") as HTMLTextAreaElement).also { textArea ->
                textArea.addEventListener("input", {
                    inputCallbacks[id]?.invoke(textArea.value)
                    window.requestAnimationFrame {
                        window.requestAnimationFrame {
                            textArea.setAttribute(
                                "data-appium-input-delivered-length",
                                textArea.value.length.toString(),
                            )
                        }
                    }
                })
            }
        } else {
            (document.createElement("button") as HTMLElement).also { button ->
                button.setAttribute("type", "button")
                button.addEventListener("click", {
                    actionCallbacks[id]?.invoke()
                })
            }
        }
    }

    private fun HTMLElement.updateInputAttributes(id: Long, input: Boolean) {
        if (input) {
            setAttribute("data-appium-input", "true")
            val value = inputValues[id].orEmpty()
            val textArea = this as HTMLTextAreaElement
            if (textArea.value != value) {
                textArea.value = value
            }
            setAttribute("data-appium-input-compose-length", value.length.toString())
        } else {
            removeAttribute("data-appium-input")
            removeAttribute("data-appium-input-compose-length")
            removeAttribute("data-appium-input-delivered-length")
        }
    }

    private fun HTMLElement.updateLayout(
        input: Boolean,
        action: Boolean,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ) {
        style.apply {
            position = "fixed"
            this.left = "${left}px"
            this.top = "${top}px"
            this.width = "${width}px"
            this.height = "${height}px"
            setProperty("pointer-events", if (input || action) "auto" else "none")
            opacity = "0.01"
            zIndex = "2147483647"
        }
    }

    private fun isInViewport(left: Float, top: Float, width: Float, height: Float): Boolean =
        width > 0f &&
            height > 0f &&
            left + width > 0f &&
            top + height > 0f &&
            left < window.innerWidth &&
            top < window.innerHeight

    private fun HTMLElement.updateAttachment(inViewport: Boolean) {
        if (inViewport && parentElement == null) {
            removeAttribute("aria-hidden")
            overlay().appendChild(this)
        } else if (!inViewport && parentElement != null) {
            setAttribute("aria-hidden", "true")
            remove()
        }
    }

    actual fun updateInput(id: Long, value: String, onValueChange: ((String) -> Unit)?) {
        if (!isEnabled()) return
        inputValues[id] = value
        if (onValueChange != null) {
            inputCallbacks[id] = onValueChange
        } else {
            inputCallbacks.remove(id)
        }
        val textArea = elements[id] as? HTMLTextAreaElement ?: return
        if (textArea.value != value) {
            textArea.value = value
        }
        textArea.setAttribute("data-appium-input-compose-length", value.length.toString())
    }

    actual fun updateAction(id: Long, onClick: (() -> Unit)?) {
        if (!isEnabled()) return
        if (onClick != null) {
            actionCallbacks[id] = onClick
        } else {
            actionCallbacks.remove(id)
        }
    }

    actual fun remove(id: Long) {
        inputCallbacks.remove(id)
        inputValues.remove(id)
        actionCallbacks.remove(id)
        elements.remove(id)?.remove()
    }

    private fun isEnabled(): Boolean = window.location.search.contains("appium=true")

    private fun HTMLElement.setOrRemoveAttribute(name: String, value: String?) {
        if (value == null) {
            removeAttribute(name)
        } else {
            setAttribute(name, value)
        }
    }

    private fun HTMLElement.setBooleanAttribute(name: String, value: Boolean) {
        if (value) {
            setAttribute(name, "true")
        } else {
            removeAttribute(name)
        }
    }

    private fun overlay(): HTMLElement = (document.getElementById(OVERLAY_ID) as? HTMLElement)
        ?: (document.createElement("div") as HTMLElement).also { element ->
            element.id = OVERLAY_ID
            element.style.setProperty("pointer-events", "none")
            document.body?.appendChild(element)
        }
}
