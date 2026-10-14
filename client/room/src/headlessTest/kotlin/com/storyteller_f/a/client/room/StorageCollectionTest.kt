/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.client.room

import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.storage.RoomCollection
import com.storyteller_f.storage.TitleCollection
import com.storyteller_f.storage.getName
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

class StorageCollectionTest {
    @Test
    fun roomRefreshPreservesDetailsAndOtherLists() =
        runTest {
        val database = getRoomDatabase("collection-${Uuid.random().toHexString()}")
        try {
            val collection = RoomCollection.SearchRoom("", null, JoinStatusSearch.JOINED)
            val other = RoomCollection.CommunityRooms(2)
            assertCollectionCleanup(database, RoomCollection.Rooms.getName(), collection.getName(), other.getName()) {
                RoomRoomInfoStorage(database).clean(collection)
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun titleRefreshPreservesDetailsAndOtherLists() =
        runTest {
        val database = getRoomDatabase("collection-${Uuid.random().toHexString()}")
        try {
            val collection = TitleCollection.SearchTitle(1, TitleSearchType.CREATOR)
            assertCollectionCleanup(
                database,
                TitleCollection.Titles.getName(),
                collection.getName(),
                TitleCollection.AllTitles.getName(),
            ) {
                RoomTitleInfoStorage(database).clean(collection)
            }
        } finally {
            database.close()
        }
    }

    private suspend fun assertCollectionCleanup(
        database: AppDatabase,
        defaultCollection: String,
        selectedCollection: String,
        otherCollection: String,
        clean: suspend () -> Unit,
    ) {
        val dao = database.getCommonDao()
        listOf(defaultCollection, selectedCollection, otherCollection).forEach { collection ->
            dao.insert(CommonEntity(1, collection, "preserved"))
        }
        dao.insert(CommonEntity("alias", defaultCollection, "preserved-alias"))
        clean()
        assertNull(dao.get(selectedCollection, "1"))
        assertEquals("preserved", dao.get(defaultCollection, "1")?.data)
        assertEquals("preserved-alias", dao.get(defaultCollection, "alias")?.data)
        assertEquals("preserved", dao.get(otherCollection, "1")?.data)
    }
}
