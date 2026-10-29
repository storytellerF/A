/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.storage

import com.storyteller_f.shared.model.TopicActivity
import com.storyteller_f.shared.model.TopicDetail

/** Activity responses do not load replies and must not replace already cached reply previews. */
suspend fun TopicInfoStorage.mergeActivity(summary: TopicActivity): TopicDetail {
    val cached = getDocument(TopicCollection.Topics, summary.id)
    return summary.withCachedReplies(cached)
}
