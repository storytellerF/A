package com.storyteller_f.index

import com.storyteller_f.shared.type.PrimaryKey
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.DirectoryReader
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
            IndexWriter(FSDirectory.open(path), IndexWriterConfig(analyzer)).use { writer ->
                writer.addDocuments(
                    topics.map {
                        val doc = Document()
                        doc.add(LongField("id", it.id.toLong(), Field.Store.YES))
                        doc.add(TextField("content", it.content, Field.Store.YES))
                        doc
                    }
                )
            }
        }
    }

    override suspend fun getDocument(idList: List<PrimaryKey>): List<TopicDocument?> {
        return FSDirectory.open(path).use {
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
        }
    }

    override suspend fun clean() {
        FSDirectory.open(path).use {
            IndexWriter(FSDirectory.open(path), IndexWriterConfig(analyzer)).use { writer ->
                writer.deleteDocuments(MatchAllDocsQuery())
            }
        }
    }
}
