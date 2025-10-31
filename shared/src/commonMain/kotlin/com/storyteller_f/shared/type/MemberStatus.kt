package com.storyteller_f.shared.type

/**
 * INVITED 是为了用户未加入时也能够在列表中看到，此时应该是有Title 的，用户可以选择加入，
 * 如果没有就无法加入，只能查看，用于Notification
 */
enum class MemberStatus {
    JOINED, INVITED
}
