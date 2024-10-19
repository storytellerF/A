package com.storyteller_f.naming

import com.storyteller_f.shared.type.PrimaryKey

class NameService {
    private val nameMap = mutableMapOf<Int, Int>()
    private val countList = mutableListOf<Int>()

    init {
        this::class.java.classLoader.getResourceAsStream("charset")!!.bufferedReader().use {
            it.readText().split("\n").filterIndexed { index, element ->
                index != 0 && !element.startsWith("//")
            }.map {
                val split = it.trim().split(Regex("[ \\t]"))
                split[1].dropLast(1).toInt() to split[2].split("-")
            }.fold(0) { acc, i ->
                val count = acc + i.first
                countList.add(count)
                val leftRange = i.second.first().toInt(16)
                nameMap[count] = leftRange
                count
            }
        }
    }

    fun parse(id: ULong): String {
        return numberToCustomCharset(id)
    }

    // 将数字转换为自定义字符集表示的字符串
    private fun numberToCustomCharset(num: PrimaryKey): String {
        val base = countList.last().toULong()

        var number = num

        assert(num > 0u)

        val result = StringBuilder()
        while (number > 0u) {
            val remainder = (number % base).toInt()
            val i = countList.indexOfFirst {
                it > remainder
            }
            val leftRange = nameMap[countList[i]]!!
            val toChar = (leftRange + remainder - countList.getOrElse(i - 1) { 0 }).toChar()
            result.append(toChar)
            number /= base
        }

        // 因为进制转换是从低位到高位进行的，所以结果需要反转
        return result.reverse().toString()
    }
}
