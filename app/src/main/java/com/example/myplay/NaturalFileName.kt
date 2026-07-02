package com.example.myplay

import java.text.Collator
import java.util.Locale

object NaturalFileName {
    private val collator = Collator.getInstance(Locale.CHINA)
    private val episodeRegex = Regex("第\\s*([0-9０-９一二两三四五六七八九十百千万零〇]+)\\s*[集章节回话]")
    private val leadingNumberRegex = Regex("^\\D*([0-9０-９]+)")

    fun compare(left: String, right: String): Int {
        val leftEpisode = episodeNumber(left)
        val rightEpisode = episodeNumber(right)
        if (leftEpisode != null && rightEpisode != null && leftEpisode != rightEpisode) {
            return leftEpisode.compareTo(rightEpisode)
        }

        val leftTokens = tokenize(left)
        val rightTokens = tokenize(right)
        val count = minOf(leftTokens.size, rightTokens.size)
        for (i in 0 until count) {
            val a = leftTokens[i]
            val b = rightTokens[i]
            val result = when {
                a.number != null && b.number != null -> a.number.compareTo(b.number)
                a.number == null && b.number == null -> collator.compare(a.text, b.text)
                a.number != null -> -1
                else -> 1
            }
            if (result != 0) return result
        }
        return if (leftTokens.size != rightTokens.size) {
            leftTokens.size.compareTo(rightTokens.size)
        } else {
            collator.compare(left, right)
        }
    }

    private fun episodeNumber(name: String): Long? {
        val normalized = normalizeDigits(name)
        val fromEpisode = episodeRegex.find(normalized)?.groupValues?.getOrNull(1)?.let(::parseNumber)
        if (fromEpisode != null) return fromEpisode
        return leadingNumberRegex.find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun tokenize(value: String): List<Token> {
        val normalized = normalizeDigits(value).lowercase(Locale.CHINA)
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < normalized.length) {
            val c = normalized[i]
            if (c.isDigit()) {
                val start = i
                while (i < normalized.length && normalized[i].isDigit()) i++
                tokens.add(Token(normalized.substring(start, i), normalized.substring(start, i).toLongOrNull() ?: Long.MAX_VALUE))
            } else {
                val start = i
                while (i < normalized.length && !normalized[i].isDigit()) i++
                tokens.add(Token(normalized.substring(start, i), null))
            }
        }
        return tokens
    }

    private fun normalizeDigits(value: String): String = buildString(value.length) {
        value.forEach { c ->
            append(if (c in '０'..'９') '0' + (c - '０') else c)
        }
    }

    private fun parseNumber(value: String): Long? {
        value.toLongOrNull()?.let { return it }
        var total = 0L
        var section = 0L
        var number = 0L
        value.forEach { c ->
            when (c) {
                '零', '〇' -> number = 0
                '一' -> number = 1
                '二', '两' -> number = 2
                '三' -> number = 3
                '四' -> number = 4
                '五' -> number = 5
                '六' -> number = 6
                '七' -> number = 7
                '八' -> number = 8
                '九' -> number = 9
                '十' -> {
                    section += if (number == 0L) 10 else number * 10
                    number = 0
                }
                '百' -> {
                    section += number * 100
                    number = 0
                }
                '千' -> {
                    section += number * 1000
                    number = 0
                }
                '万' -> {
                    total += (section + number) * 10000
                    section = 0
                    number = 0
                }
                else -> return null
            }
        }
        return total + section + number
    }

    private data class Token(val text: String, val number: Long?)
}
