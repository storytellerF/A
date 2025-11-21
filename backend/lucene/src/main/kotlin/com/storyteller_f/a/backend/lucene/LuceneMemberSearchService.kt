package com.storyteller_f.a.backend.lucene

import com.storyteller_f.a.backend.core.Cursor
import com.storyteller_f.a.backend.core.MergedEnv
import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
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
import org.apache.lucene.search.SortField
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
                objectName = document.get("objectName")
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
                    "lucene delete member document: uid=$uid, objectId=$objectId, deleted=$deletedCount"
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
        memberDocumentSearch: MemberDocumentSearch,
        primaryKeyFetch: PrimaryKeyFetch?
    ): Result<PaginationResult<MemberDocument>> {
        val combinedQuery = buildQuery(primaryKeyFetch, memberDocumentSearch)
        val reverse = when {
            primaryKeyFetch == null || primaryKeyFetch.cursor == null -> true
            primaryKeyFetch.cursor is Cursor.DescCursor<PrimaryKey> -> true
            else -> false
        }
        val sortById = Sort(SortField("id2", SortField.Type.LONG, reverse))
        Napier.i {
            "lucene search member query $combinedQuery $sortById $reverse"
        }
        return useLucene {
            searchDocumentList(combinedQuery, primaryKeyFetch, sortById, LuceneMemberDocument)
        }
    }

    private fun buildQuery(
        primaryKeyFetch: PrimaryKeyFetch?,
        memberDocumentSearch: MemberDocumentSearch
    ): Query {
        return buildPrimaryKeyLuceneSearchQuery(primaryKeyFetch) {
            when (memberDocumentSearch) {
                is MemberDocumentSearch.Keyword -> {
                    // 按 objectId 搜索
                    memberDocumentSearch.objectId?.let { objId ->
                        add(
                            LongPoint.newExactQuery("objectId", objId),
                            BooleanClause.Occur.MUST
                        )
                    }
                    // 按 nickname 搜索
                    preprocessUserInputKeyword(memberDocumentSearch.nickname?.let { listOf(it) })?.let {
                        add(
                            MultiFieldQueryParser(
                                arrayOf("nickname"),
                                analyzer
                            ).parse(it),
                            BooleanClause.Occur.MUST
                        )
                    }
                }
                is MemberDocumentSearch.CommunityMembers -> {
                    // 按 uid 搜索（该用户加入的社区）
                    add(
                        LongPoint.newExactQuery("uid", memberDocumentSearch.uid),
                        BooleanClause.Occur.MUST
                    )
                    // 按 objectType 搜索（只搜索社区）
                    add(
                        TermQuery(Term("objectType", ObjectType.COMMUNITY.name)),
                        BooleanClause.Occur.MUST
                    )
                    // 按社区名称搜索
                    preprocessUserInputKeyword(listOf(memberDocumentSearch.objectName))?.let {
                        add(
                            MultiFieldQueryParser(
                                arrayOf("objectName"),
                                analyzer
                            ).parse(it),
                            BooleanClause.Occur.MUST
                        )
                    }
                }
            }
        }
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
