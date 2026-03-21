package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.client.core.UploadData
import com.storyteller_f.a.client.core.getUserCommunities
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.searchCommunity
import com.storyteller_f.a.client.core.updateCommunityInfo
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.model.PosterSearch
import com.storyteller_f.shared.obj.UpdateCommunityBody
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.JoinStatusSearch
import com.storyteller_f.shared.type.ObjectType
import io.ktor.http.ContentType
import io.ktor.utils.io.streams.asInput
import kotlin.test.Test
import kotlin.test.assertEquals

class CommunitySearchTest {

    @Test
    fun `test search community with different strategies`() = test {
        // 创建测试数据
        val session = attachSession {
            val community1 = createCommunityForTest("test community 1", "tc1")
            val community2 = createCommunityForTest("test community 2", "tc2")
            val community3 = createCommunityForTest("another community", "ac1")
            Triple(community1.id, community2.id, community3.id)
        }

        attachSession {
            val (community1Id, community2Id) = session.custom

            // 加入前两个社区
            joinCommunity(community1Id).getOrThrow()
            joinCommunity(community2Id).getOrThrow()

            // 测试情况1: 使用新的 getUserCommunities API 获取已加入的社区（不带关键字）
            val result1 = getUserCommunities(
                query = CustomApi.Users.JoinedCommunities.UserCommunitiesQuery(size = 10)
            ).getOrThrow()
            assertEquals(2, result1.data.size, "Should find 2 joined communities")

            // 测试情况2: word 不为空 && 搜索已加入的社区，使用 memberSearchService
            // 搜索已加入的社区，带关键字"test"
            val result2 = searchCommunity(
                size = 10,
                joinStatusSearch = JoinStatusSearch.JOINED,
                word = "test"
            ).getOrThrow()
            assertEquals(2, result2.data.size, "Should find 2 joined communities matching 'test'")

            // 测试情况3: word 不为空 && 不是搜索已加入（Unspecified），使用 communitySearchService
            // 搜索所有社区，带关键字"community"
            val result3 = searchCommunity(
                size = 10,
                joinStatusSearch = JoinStatusSearch.UNSPECIFIED,
                word = "community"
            ).getOrThrow()
            assertEquals(3, result3.data.size, "Should find 3 communities matching 'community'")

            // 搜索所有社区，带关键字"another"
            val result4 = searchCommunity(
                size = 10,
                joinStatusSearch = JoinStatusSearch.UNSPECIFIED,
                word = "another"
            ).getOrThrow()
            assertEquals(1, result4.data.size, "Should find 1 community matching 'another'")
        }
    }

    @Test
    fun `test get user communities with poster filter`() = test {
        val session = attachSession {
            val community1 = createCommunityForTest("no poster community", "nop1")

            val community2 = createCommunityForTest("has poster community", "hap1")

            val inputStream = ClassLoader.getSystemResourceAsStream("cover.jpg")!!
            val buf = java.io.ByteArrayOutputStream()
            val image = javax.imageio.ImageIO.read(inputStream).getSubimage(0, 0, 300, 400)
            val out = java.io.File("build/test/poster.jpg")
            out.parentFile?.mkdirs()
            javax.imageio.ImageIO.write(image, "jpg", out)
            javax.imageio.ImageIO.write(image, "jpg", buf)
            val inputBytes = java.io.ByteArrayInputStream(buf.toByteArray())
            val fileInfo = upload(it.uid ob ObjectType.USER, UploadData(
                buf.size().toLong(),
                "cover.jpg",
                ContentType.Image.JPEG
            ) {
                inputBytes.asInput()
            }).getOrThrow().data.first()

            updateCommunityInfo(community2.id, UpdateCommunityBody(poster = fileInfo.id)).getOrThrow()

            Triple(it.uid, community1.id, community2.id)
        }

        attachSession {
            val (_, c1, c2) = session.custom
            joinCommunity(c1).getOrThrow()
            joinCommunity(c2).getOrThrow()

            // Get all user communities
            val all = getUserCommunities(
                CustomApi.Users.JoinedCommunities.UserCommunitiesQuery(size = 10)
            ).getOrThrow().data
            assertEquals(2, all.size, "Should get 2 joined communities")

            // Get only has poster communities
            val onlyPoster = getUserCommunities(
                CustomApi.Users.JoinedCommunities.UserCommunitiesQuery(hasPoster = PosterSearch.HAS_POSTER, size = 10)
            ).getOrThrow().data
            assertEquals(1, onlyPoster.size, "Should get 1 community with poster")
            assertEquals(c2, onlyPoster.first().id)

            // Optional: Get no poster communities
            val noPoster = getUserCommunities(
                CustomApi.Users.JoinedCommunities.UserCommunitiesQuery(hasPoster = PosterSearch.NO_POSTER, size = 10)
            ).getOrThrow().data
            assertEquals(1, noPoster.size, "Should get 1 community without poster")
            assertEquals(c1, noPoster.first().id)
        }
    }
}
