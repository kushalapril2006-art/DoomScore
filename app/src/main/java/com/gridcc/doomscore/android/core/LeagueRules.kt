package com.gridcc.doomscore.android.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.Locale
import kotlin.math.ceil

object LeagueRules {
    fun instagram(value: String): String {
        require(value.length <= 64) { "Use an Instagram username, not a link." }
        return value.trim().removePrefix("@").lowercase(Locale.ROOT).also {
            require(it.isEmpty() || Regex("[a-z0-9_](?:[a-z0-9._]{0,28}[a-z0-9_])?").matches(it) && !it.contains("..")) {
                "Use an Instagram username of up to 30 letters, numbers, dots or underscores."
            }
        }
    }
    fun month(now: Instant = Instant.now()): YearMonth = YearMonth.from(now.atOffset(ZoneOffset.UTC))
    fun spotlight(now: Instant): Boolean = now.atOffset(ZoneOffset.UTC).dayOfMonth <= 7
    fun rankLabel(rank: Long?, people: Long): String = when {
        rank == null || people == 0L -> "unranked"
        rank <= 200 -> "#$rank"
        else -> "top ${ceil(rank.toDouble() * 100 / people).toInt().coerceIn(1, 100)}%"
    }
    fun inSeason(day: LocalDate, month: YearMonth) = YearMonth.from(day) == month
}
