package com.storyteller_f.a.cloud.server

import com.storyteller_f.a.api.CustomApi
import com.storyteller_f.a.api.NewCommunity
import com.storyteller_f.a.api.NewRoom
import com.storyteller_f.a.client.core.createCommunity
import com.storyteller_f.a.client.core.createRoom
import com.storyteller_f.a.client.core.joinCommunity
import com.storyteller_f.a.client.core.joinRoom
import com.storyteller_f.a.client.core.searchFiles
import com.storyteller_f.a.client.core.upload
import com.storyteller_f.shared.obj.ob
import com.storyteller_f.shared.type.ObjectType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileSearchTest {

    @Test
    fun `test search files by name`() = test {
        attachSession {
            // 上传多个文件
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("file1", "test1.txt")).getOrThrow()
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("file2", "document.txt")).getOrThrow()
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("file3", "test2.txt")).getOrThrow()

            // 搜索所有文件（不指定关键词）
            val allFiles = searchFiles(
                CustomApi.Files.FileSearchQuery(
                    word = null,
                    objectId = it.uid,
                    objectType = ObjectType.USER
                )
            ).getOrThrow().data
            assertEquals(3, allFiles.size)

            // 按名称搜索
            val testFiles = searchFiles(
                CustomApi.Files.FileSearchQuery(
                    word = "test",
                    objectId = it.uid,
                    objectType = ObjectType.USER
                )
            ).getOrThrow().data
            assertEquals(2, testFiles.size)
            assertTrue(testFiles.all { file -> file.name.contains("test") })

            // 搜索特定文件
            val docFiles = searchFiles(
                CustomApi.Files.FileSearchQuery(
                    word = "document",
                    objectId = it.uid,
                    objectType = ObjectType.USER
                )
            ).getOrThrow().data
            assertEquals(1, docFiles.size)
            assertEquals("document.txt", docFiles.first().name)
        }
    }

    @Test
    fun `test search files only returns user own files`() = test {
        attachSession {
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("user1 file", "user1.txt")).getOrThrow()
        }

        attachSession {
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("user2 file", "user2.txt")).getOrThrow()

            // 搜索自己的文件，不应包含其他用户的文件
            val myFiles = searchFiles(
                CustomApi.Files.FileSearchQuery(word = "user")
            ).getOrThrow().data

            assertEquals(1, myFiles.size)
            assertEquals("user2.txt", myFiles.first().name)
            assertEquals(it.uid, myFiles.first().owner)
        }
    }

    @Test
    fun `test search room files with permission check`() = test {
        val sessionOuterTuple = attachSession {
            val communityId = createCommunity(NewCommunity("c1", "c1")).getOrThrow().id
            val roomId = createRoom(NewRoom("r1", "room1", communityId = communityId)).getOrThrow().id

            // 上传文件到房间
            upload(roomId ob ObjectType.ROOM, getUploadDataFromText("room file1", "room1.txt")).getOrThrow()
            upload(roomId ob ObjectType.ROOM, getUploadDataFromText("room file2", "room2.txt")).getOrThrow()

            communityId to roomId
        }

        val communityId = sessionOuterTuple.custom.first
        val roomId = sessionOuterTuple.custom.second

        // 另一个用户加入社区和房间后应该能搜索房间文件
        attachSession {
            joinCommunity(communityId).getOrThrow()
            joinRoom(roomId).getOrThrow()

            val roomFiles = searchFiles(
                CustomApi.Files.FileSearchQuery(
                    word = "room",
                    objectId = roomId,
                    objectType = ObjectType.ROOM
                )
            ).getOrThrow().data

            assertEquals(2, roomFiles.size)
            assertTrue(roomFiles.all { it.name.contains("room") })
        }
    }

    @Test
    fun `test search files without permission fails`() = test {
        val sessionOuterTuple = attachSession {
            val communityId = createCommunity(NewCommunity("c1", "c1")).getOrThrow().id
            val roomId = createRoom(NewRoom("r1", "room1", communityId = communityId)).getOrThrow().id

            upload(roomId ob ObjectType.ROOM, getUploadDataFromText("private file", "private.txt")).getOrThrow()

            communityId to roomId
        }

        val roomId = sessionOuterTuple.custom.second

        // 未加入房间的用户不应能搜索房间文件
        attachSession {
            val result = searchFiles(
                CustomApi.Files.FileSearchQuery(
                    word = "private",
                    objectId = roomId,
                    objectType = ObjectType.ROOM
                )
            )

            assertTrue(result.isFailure)
        }
    }

    @Test
    fun `test search files with empty keyword returns all files`() = test {
        attachSession {
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("content1", "file1.txt")).getOrThrow()
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("content2", "file2.txt")).getOrThrow()
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("content3", "file3.txt")).getOrThrow()

            // 空关键词应返回所有文件
            val allFiles = searchFiles(
                CustomApi.Files.FileSearchQuery(
                    word = "",
                    objectId = it.uid,
                    objectType = ObjectType.USER
                )
            ).getOrThrow().data

            assertEquals(3, allFiles.size)
        }
    }

    @Test
    fun `test search files case insensitive`() = test {
        attachSession {
            upload(it.uid ob ObjectType.USER, getUploadDataFromText("content", "TestFile.txt")).getOrThrow()

            // 搜索应该不区分大小写
            val upperCase = searchFiles(
                CustomApi.Files.FileSearchQuery(
                    word = "TEST",
                    objectId = it.uid,
                    objectType = ObjectType.USER
                )
            ).getOrThrow().data

            val lowerCase = searchFiles(
                CustomApi.Files.FileSearchQuery(
                    word = "test",
                    objectId = it.uid,
                    objectType = ObjectType.USER
                )
            ).getOrThrow().data

            assertEquals(1, upperCase.size)
            assertEquals(1, lowerCase.size)
            assertEquals(upperCase.first().id, lowerCase.first().id)
        }
    }
}
