package com.storyteller_f.shared.utils

fun <T1, T2> List<Pair<T1, T2>>.associateByPair(): Map<T1, T2> {
    return associate {
        it
    }
}

fun <T1, T2> List<Pair<T1, T2>>.groupByPair(): Map<T1, List<T2>> {
    return groupBy { it.first }.mapValues {
        it.value.map { p ->
            p.second
        }
    }
}
