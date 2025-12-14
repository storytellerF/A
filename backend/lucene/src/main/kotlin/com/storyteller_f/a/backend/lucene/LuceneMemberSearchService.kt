package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.preprocessUserInputKeyword
import com.storyteller_f.a.backend.core.service.MemberDocument
import com.storyteller_f.a.backend.core.service.MemberDocumentSearch
import com.storyteller_f.a.backend.core.service.MemberSearchService
import com.storyteller_f.a.backend.core.service.MemberSearchServiceFactory
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.LongPoint
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.Sort
import org.apache.lucene.search.TermQuery
import java.nio.file.Path

data class LuceneMemberDocument(val memberDocument: MemberDocument) :
    LuceneDocument {
    override fun save(): Document {
        val id = memberDocument.id
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(LongField("uid", memberDocument.uid, Field.Store.YES))
            add(LongField("objectId", memberDocument.objectId, Field.Store.YES))
            add(StringField("objectType", memberDocument.objectType.name, Field.Store.YES))
            add(TextField("nickname", memberDocument.nickname, Field.Store.YES))
            add(TextField("objectName", memberDocument.objectName, Field.Store.YES))
            // 添加communityId字段（如果存在）
            memberDocument.communityId?.let { communityId ->
                add(LongField("communityId", communityId, Field.Store.YES))
            }
        }
    }

    companion object : LuceneDocumentCompanion<MemberDocument> {
        override fun restore(
            id: PrimaryKey,
            document: Document
        ): MemberDocument {
            return MemberDocument(
                id = id,
                uid = document.get("uid").toLong(),
                objectId = document.get("objectId").toLong(),
                objectType = ObjectType.valueOf(document.get("objectType")),
                nickname = document.get("nickname"),
                objectName = document.get("objectName"),
                communityId = document.get("communityId")?.toLong()
            )
        }
    }
}

class LuceneMemberSearchService(path: Path, isInMemory: Boolean = false) : Lucene(path, isInMemory),
    MemberSearchService {
    override suspend fun saveDocument(documents: List<MemberDocument>): Result<Unit> {
        return useLucene {
            saveDocumentList(documents.map {
                LuceneMemberDocument(it)
            }, analyzer)
        }
    }

    override suspend fun deleteDocument(uid: PrimaryKey, objectId: PrimaryKey): Result<Unit> {
        return useLucene {
            IndexWriter(this, IndexWriterConfig(analyzer)).use { writer ->
                val query = BooleanQuery.Builder().apply {
                    add(LongPoint.newExactQuery("uid", uid), BooleanClause.Occur.MUST)
                    add(LongPoint.newExactQuery("objectId", objectId), BooleanClause.Occur.MUST)
                }.build()
                val deletedCount = writer.deleteDocuments(query)
                Napier.d {
                    "lucene delete member document: query=$query, deleted=$deletedCount"
                }
            }
        }
    }

    override suspend fun clean(): Result<Unit> {
        return useLucene {
            cleanAll(analyzer)
        }
    }

    override suspend fun searchDocument(
        memberDocumentSearch: MemberDocumentSearch
    ): Result<PaginationResult<MemberDocument>> {
        if (memberDocumentSearch is MemberDocumentSearch.Keyword && memberDocumentSearch.nickname.isEmpty()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        if (memberDocumentSearch is MemberDocumentSearch.CommunityMembers &&
            memberDocumentSearch.objectName.isBlank()
        ) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        if (memberDocumentSearch is MemberDocumentSearch.CommunityMembers &&
            memberDocumentSearch.objectName.isBlank()) {
            return Result.success(PaginationResult(emptyList(), 0))
        }
        val combinedQuery = buildQuery(memberDocumentSearch)

        Napier.i {
            "lucene search member query $combinedQuery"
        }
        return useLucene {
            val (fetch, sort) = when (memberDocumentSearch) {
                is MemberDocumentSearch.Keyword -> {
                    memberDocumentSearch.fetch to Sort.RELEVANCE
                }

                is MemberDocumentSearch.CommunityMembers -> {
                    memberDocumentSearch.fetch to Sort.RELEVANCE
                }

                is MemberDocumentSearch.RoomMembers -> {
                    memberDocumentSearch.fetch to Sort.RELEVANCE
                }
            }
            searchDocumentList(combinedQuery, fetch, sort, LuceneMemberDocument)
        }
    }

    private fun buildQuery(
        memberDocumentSearch: MemberDocumentSearch
    ): Query {
        return BooleanQuery.Builder().apply {
            when (memberDocumentSearch) {
                is MemberDocumentSearch.Keyword -> {
                    // 按 objectId 搜索
                    memberDocumentSearch.objectId?.let { objId ->
                        add(LongPoint.newExactQuery("objectId", objId), BooleanClause.Occur.MUST)
                    }
                    // 按 nickname 搜索
                    val keyword = preprocessUserInputKeyword(memberDocumentSearch.nickname)
                    add(MultiFieldQueryParser(arrayOf("nickname"), analyzer).parse(keyword), BooleanClause.Occur.MUST)
                }

                is MemberDocumentSearch.CommunityMembers -> {
                    addUidQuery(memberDocumentSearch.uid)
                    addObjectTypeQuery(ObjectType.COMMUNITY)
                    addObjectNameQuery(memberDocumentSearch.objectName)
                }

                is MemberDocumentSearch.RoomMembers -> {
                    addUidQuery(memberDocumentSearch.uid)
                    addObjectTypeQuery(ObjectType.ROOM)
                    addObjectNameQuery(memberDocumentSearch.objectName)
                    // 添加对communityId的过滤
                    memberDocumentSearch.communityId?.let { communityId ->
                        add(LongField.newExactQuery("communityId", communityId), BooleanClause.Occur.MUST)
                    }
                }
            }
        }.build()
    }

    private fun BooleanQuery.Builder.addUidQuery(uid: PrimaryKey) {
        add(LongPoint.newExactQuery("uid", uid), BooleanClause.Occur.MUST)
    }

    private fun BooleanQuery.Builder.addObjectTypeQuery(objectType: ObjectType) {
        add(TermQuery(Term("objectType", objectType.name)), BooleanClause.Occur.MUST)
    }

    private fun BooleanQuery.Builder.addObjectNameQuery(name: String) {
        val keyword = preprocessUserInputKeyword(name)
        add(MultiFieldQueryParser(arrayOf("objectName"), analyzer).parse(keyword), BooleanClause.Occur.MUST)
    }
}

class LuceneMemberSearchServiceFactory : MemberSearchServiceFactory {
    override fun match(env: MergedEnv): Boolean {
        return env["SEARCH_SERVICE"] == "lucene"
    }

    override fun build(env: MergedEnv): MemberSearchService {
        return buildLuceneSearchService(env) { path, isInMemory ->
            LuceneMemberSearchService(path, isInMemory)
        }
    }
}
