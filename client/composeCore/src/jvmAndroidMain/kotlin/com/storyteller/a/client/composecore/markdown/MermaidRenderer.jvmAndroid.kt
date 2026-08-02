/*
 * This is a private project. All rights reserved.
 */

package com.storyteller.a.client.composecore.markdown

import com.storyteller_f.mermaid_kmp.renderMermaid

internal actual fun renderMermaidSvg(input: String): String = renderMermaid(input)
