package com.storyteller_f.index

import com.storyteller_f.shared.type.PrimaryKey
import io.github.aakira.napier.Napier
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexNotFoundException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.MatchAllDocsQuery
import org.apache.lucene.store.FSDirectory
import java.nio.file.Path

class LuceneTopicDocumentService(private val path: Path) : TopicDocumentService {
    private val analyzer = StandardAnalyzer()

    override suspend fun saveDocument(topics: List<TopicDocument>) {
        FSDirectory.open(path).use {
            IndexWriter(it, IndexWriterConfig(analyzer)).use { writer ->
                val addDocuments = writer.addDocuments(
                    topics.map { document ->
                        val doc = Document()
                        doc.add(LongField("id", document.id.toLong(), Field.Store.YES))
                        doc.add(TextField("content", document.content, Field.Store.YES))
                        doc
                    }
                )
                Napier.d {
                    "save document $addDocuments in `lucene`"
                }
            }
        }
    }

    override suspend fun getDocument(idList: List<PrimaryKey>): List<TopicDocument?> {
        if (idList.isEmpty()) return emptyList()
        return FSDirectory.open(path).use {
            try {
                DirectoryReader.open(it).use { reader ->
                    val searcher = IndexSearcher(reader)
                    idList.map { id ->
                        val topDocs = searcher.search(LongPoint.newExactQuery("id", id.toLong()), 1)
                        topDocs.scoreDocs.firstOrNull()?.let { scoreDoc ->
                            searcher.storedFields().document(scoreDoc.doc).get("content")?.let { content ->
                                TopicDocument(id, content)
                            }
                        }
                    }
                }
            } catch (e: IndexNotFoundException) {
                List(idList.size) {
                    null
                }
            }
        }
    }

    override suspend fun clean() {
        FSDirectory.open(path).use {
            IndexWriter(it, IndexWriterConfig(analyzer)).use { writer ->
                writer.deleteDocuments(MatchAllDocsQuery())
            }
        }
    }
}
