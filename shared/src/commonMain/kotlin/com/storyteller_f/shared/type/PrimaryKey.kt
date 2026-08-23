/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.type

typealias PrimaryKey = Long

const val DEFAULT_PRIMARY_KEY: Long = 0L

fun String.toPrimaryKey() = toLong()

fun String.toPrimaryKeyOrNull() = toLongOrNull()
