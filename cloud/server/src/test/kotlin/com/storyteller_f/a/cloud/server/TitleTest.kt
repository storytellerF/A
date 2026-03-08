package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewFavorite
import com.storyteller_f.a.api.NewSubscription
import com.storyteller_f.a.api.NewTitle
import com.storyteller_f.a.api.PaginationQuery
import com.storyteller_f.a.client.core.addFavorite
import com.storyteller_f.a.client.core.addSubscription
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createTitle
import com.storyteller_f.a.client.core.getFavorites
import com.storyteller_f.a.client.core.getSubscriptions
import com.storyteller_f.a.client.core.removeFavorite
import com.storyteller_f.a.client.core.removeSubscription
import com.storyteller_f.a.client.core.userTitles
import com.storyteller_f.shared.model.TitleSearchType
import com.storyteller_f.shared.model.TitleType
import com.storyteller_f.shared.type.ObjectType
import kotlin.test.Test
import kotlin.test.assertNotNull

class TitleTest {
    @Test
    fun `test title`() = test {
        attachSession {
            val c = createCommunity(NewCommunity("c1", "c1")).getOrThrow()
            val cId = c.id
            assertListSize(0, userTitles(it.uid, 10, TitleSearchType.RECEIVER))
            createTitle(NewTitle("c KOL", TitleType.REGULAR, it.uid, cId, ObjectType.COMMUNITY, "hello")).getOrThrow()
            assertListTotalSize(1, userTitles(it.uid, 10, TitleSearchType.RECEIVER))
            assertListSize(1, userTitles(it.uid, 10, TitleSearchType.RECEIVER))
            assertListSize(1, userTitles(it.uid, 10, TitleSearchType.CREATOR))
            assertListSize(1, userTitles(it.uid, 10, TitleSearchType.CREATOR, scopeId = cId))
            assertListSize(1, userTitles(it.uid, 10, TitleSearchType.CREATOR, type = TitleType.REGULAR, scopeId = cId))
        }
    }

    @Test
    fun `test add title favorite`() = test {
        attachSession {
            val c = createCommunity(NewCommunity("fav1", "fav1")).getOrThrow()
            val cId = c.id
            createTitle(
                NewTitle("c KOL fav", TitleType.REGULAR, it.uid, cId, ObjectType.COMMUNITY, "hello fav")
            ).getOrThrow()

            val titleId = userTitles(
                it.uid,
                10,
                TitleSearchType.RECEIVER
            ).getOrThrow().data.first { t -> t.name == "c KOL fav" }.id

            addFavorite(NewFavorite(ObjectType.TITLE, titleId)).getOrThrow()
            assertListTotalSize(1, getFavorites(PaginationQuery()))

            val titleInfo = userTitles(
                it.uid,
                10,
                TitleSearchType.RECEIVER
            ).getOrThrow().data.first { t -> t.id == titleId }
            assertNotNull(titleInfo.favoriteId)

            removeFavorite(titleId, ObjectType.TITLE).getOrThrow()
            assertListTotalSize(0, getFavorites(PaginationQuery()))
        }
    }

    @Test
    fun `test add title subscription`() = test {
        attachSession {
            val c = createCommunity(NewCommunity("sub1", "sub1")).getOrThrow()
            val cId = c.id
            createTitle(
                NewTitle("c KOL sub", TitleType.REGULAR, it.uid, cId, ObjectType.COMMUNITY, "hello sub")
            ).getOrThrow()

            val titleId = userTitles(
                it.uid,
                10,
                TitleSearchType.RECEIVER
            ).getOrThrow().data.first { t -> t.name == "c KOL sub" }.id

            addSubscription(NewSubscription(titleId, ObjectType.TITLE)).getOrThrow()
            assertListTotalSize(1, getSubscriptions(PaginationQuery()))

            val titleInfo = userTitles(
                it.uid,
                10,
                TitleSearchType.RECEIVER
            ).getOrThrow().data.first { t -> t.id == titleId }
            assertNotNull(titleInfo.subscriptionId)

            removeSubscription(titleId, ObjectType.TITLE).getOrThrow()
            assertListTotalSize(0, getSubscriptions(PaginationQuery()))
        }
    }
}
