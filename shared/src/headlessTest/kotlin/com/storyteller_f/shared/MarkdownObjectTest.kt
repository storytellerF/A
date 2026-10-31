/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared

import com.storyteller_f.shared.model.FileInfo
import com.storyteller_f.shared.utils.MarkdownObject
import com.storyteller_f.shared.utils.extractMarkdownMediaLink
import com.storyteller_f.shared.utils.generateObjectMarkdownContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MarkdownObjectTest {
    @Test
    fun legacyTypeDoesNotBlockAttachmentParsing() {
        val legacy = """{"name":"movie.mp4","contentType":"video/mp4","title":"Movie","cover":"cover.png","url":""}"""
        val attachment = commonJson.decodeFromString<MarkdownObject>(legacy)
        assertEquals("movie.mp4", attachment.name)
        assertEquals("Movie", attachment.title)
        assertEquals("cover.png", attachment.cover)
        assertFalse(commonJson.encodeToString(attachment).contains("contentType"))
    }

    @Test
    fun generatedAttachmentEscapesFilenameAndOmitsType() {
        val name = "a \"quoted\" file.bin"
        val markdown = generateObjectMarkdownContent(FileInfo.EMPTY.copy(name = name))
        assertEquals(listOf(name), extractMarkdownMediaLink(markdown))
        assertFalse(markdown.contains("contentType"))
    }
}
