/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.model

import kotlinx.serialization.Serializable

/** Viewer membership remains in communityInfo; target membership belongs to this query result. */
@Serializable
data class CommunityMembershipItem(val communityInfo: CommunityInfo, val targetMemberInfo: NestedMemberInfo?) :
    PrimaryKeyIdentifiable {
    override val id get() = communityInfo.id
    val isJoined get() = communityInfo.isJoined
}
