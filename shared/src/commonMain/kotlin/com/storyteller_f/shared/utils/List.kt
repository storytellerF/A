/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.shared.utils

fun <T1, T2> List<Pair<T1, T2>>.associateByPair(): Map<T1, T2> =
    associate {
    it
}

fun <T1, T2> List<Pair<T1, T2>>.groupByPair(): Map<T1, List<T2>> =
    groupBy { it.first }.mapValues {
    it.value.map { p ->
        p.second
    }
}
