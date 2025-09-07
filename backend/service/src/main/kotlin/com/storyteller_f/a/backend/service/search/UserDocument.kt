package com.storyteller_f.a.backend.service.search

import com.storyteller_f.a.backend.core.PaginationResult
import com.storyteller_f.a.backend.core.PrimaryKeyFetch
import com.storyteller_f.a.backend.core.types.User
import com.storyteller_f.shared.model.PrimaryKeyIdentifiable
import com.storyteller_f.shared.type.ObjectType
import com.storyteller_f.shared.type.PrimaryKey
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.LongField
import org.apache.lucene.document.NumericDocValuesField
import org.apache.lucene.document.TextField

data class UserDocument(override val id: PrimaryKey, val nickname: String, val aid: String?) :
    PrimaryKeyIdentifiable, LuceneDocument {
    override val objectType: ObjectType = ObjectType.USER
    override fun save(): Document {
        return Document().apply {
            add(LongField("id1", id, Field.Store.YES))
            add(NumericDocValuesField("id2", id))
            add(TextField("nickname", nickname, Field.Store.YES))
            add(TextField("aid", aid, Field.Store.YES))
        }
    }

    companion object : LuceneDocumentCompanion<UserDocument> {
        override fun restore(
            id: PrimaryKey,
            document: Document
        ): UserDocument {
            return UserDocument(id, document.get("nickname"), document.get("aid"))
        }

        fun fromUser(user: User): UserDocument {
            return UserDocument(user.id, user.nickname, user.aid)
        }

    }

}

sealed interface UserDocumentSearch {
    data class Keyword(val word: List<String>? = null) : UserDocumentSearch
}

interface UserSearchService {
    suspend fun saveDocument(documents: List<UserDocument>): Result<Unit>

    suspend fun clean(): Result<Unit>

    suspend fun searchDocument(
        userDocumentSearch: UserDocumentSearch = UserDocumentSearch.Keyword(),
        primaryKeyFetch: PrimaryKeyFetch? = null
    ): Result<PaginationResult<UserDocument>>
}