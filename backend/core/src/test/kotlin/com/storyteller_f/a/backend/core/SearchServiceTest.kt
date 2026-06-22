package com.storyteller_f.a.backend.core

import com.storyteller_f.a.backend.core.service.TopicDocument
import com.storyteller_f.a.backend.core.service.TopicDocumentSearch
import com.storyteller_f.a.backend.core.service.TopicSearchService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchServiceTest {

    private val saveAndGetDocuments: suspend (TopicSearchService) -> Unit = { service ->
        val docs = listOf(
            TopicDocument(1, "hello world", 1, "ROOM", 1, "ROOM", 100),
            TopicDocument(2, "kotlin test", 1, "ROOM", 1, "ROOM", 100)
        )
        service.saveDocument(docs).getOrThrow()

        val result = service.getDocuments(listOf(1, 2)).getOrThrow()
        assertEquals(2, result.size)
        assertEquals("hello world", result[0]?.content)
        assertEquals("kotlin test", result[1]?.content)
    }

    private val saveMultiDocument: suspend (TopicSearchService) -> Unit = { service ->
        service.saveDocument(
            listOf(
                TopicDocument(0, "test", 0, "ROOM", 0, "ROOM", 0),
                TopicDocument(1, "test", 1, "ROOM", 1, "ROOM", 1)
            )
        ).getOrThrow()
    }

    private val searchDocuments: suspend (TopicSearchService) -> Unit = { service ->
        val docs = listOf(
            TopicDocument(10, "apple banana cherry", 1, "ROOM", 1, "ROOM", 100),
            TopicDocument(11, "banana orange grape", 1, "ROOM", 1, "ROOM", 101),
            TopicDocument(12, "watermelon kiwi", 1, "ROOM", 1, "ROOM", 102)
        )
        service.saveDocument(docs).getOrThrow()

        val searchResult = service.searchDocument(
            TopicDocumentSearch.All("banana", OffsetFetch(null, 10))
        ).getOrThrow()
        assertTrue(searchResult.list.isNotEmpty())
        assertTrue(searchResult.list.all { it.content.contains("banana") })
    }

    private val cleanDocuments: suspend (TopicSearchService) -> Unit = { service ->
        service.saveDocument(
            listOf(TopicDocument(20, "to be cleaned", 1, "ROOM", 1, "ROOM", 100))
        ).getOrThrow()

        service.clean().getOrThrow()

        val result = service.getDocuments(listOf(20)).getOrThrow()
        assertTrue(result.all { it == null })
    }

    @Test
    fun `test save and get documents memory`() = testSearchMemory(saveAndGetDocuments)

    @Test
    fun `test save and get documents container`() = testSearchContainer(saveAndGetDocuments)

    @Test
    fun `test save multi document memory`() = testSearchMemory(saveMultiDocument)

    @Test
    fun `test save multi document container`() = testSearchContainer(saveMultiDocument)

    @Test
    fun `test search documents memory`() = testSearchMemory(searchDocuments)

    @Test
    fun `test search documents container`() = testSearchContainer(searchDocuments)

    @Test
    fun `test clean documents memory`() = testSearchMemory(cleanDocuments)

    @Test
    fun `test clean documents container`() = testSearchContainer(cleanDocuments)
}
