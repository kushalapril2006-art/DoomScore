package com.gridcc.doomscore.android.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class SourceApp(val key: String, val label: String, val glyph: String, val packages: Set<String>) {
    INSTAGRAM("instagram", "Instagram", "◎", setOf("com.instagram.android")),
    YOUTUBE("youtube", "YouTube", "▶", setOf("com.google.android.youtube")),
    TIKTOK("tiktok", "TikTok", "♪", setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")),
    SNAPCHAT("snapchat", "Snapchat", "ϟ", setOf("com.snapchat.android"));
    companion object { fun fromPackage(p: String?) = entries.firstOrNull { p in it.packages } }
}
data class AppStats(val count: Int = 0, val watchMs: Long = 0, val ads: Int = 0, val repeats: Int = 0)
data class DayStats(val day: LocalDate, val apps: Map<SourceApp, AppStats> = emptyMap(), val hourly: List<Int> = List(24) { 0 }) {
    val total get() = apps.values.sumOf { it.count }
    val watchMs get() = apps.values.sumOf { it.watchMs }
    val ads get() = apps.values.sumOf { it.ads }
    val repeats get() = apps.values.sumOf { it.repeats }
}
data class ScrollTier(val title: String, val quip: String, val level: Int, val floor: Int, val next: Int?, val nextTitle: String?) {
    val argb: Int get() = color(level)
    fun progress(count: Int): Float = next?.let {((count.toLong()-floor).toFloat()/(it-floor)).coerceIn(0f,1f)} ?: 1f
    companion object {
        private val colors=listOf(0xFF3DE0FF,0xFFC6FF3D,0xFFFFE14D,0xFFFF8A3D,0xFFFF4D5E,0xFFFF4FB3,0xFF8A5CFF)
        fun color(level:Int)=colors[level.coerceIn(0,6)].toInt()
        private val floors=listOf(0,1,100,500,1000,2500,5000)
        private val titles=listOf("unranked","warming up","certified scroller","scroll goblin","doom lord","algorithm menace","final boss")
        private val quips=listOf("your thumb has entered the lobby 🎮","the algorithm has a new challenger ⚔","the receipts are getting loud 🔥",
            "goblin mode: officially unlocked 👹","your thumb is putting up numbers 🏆","the feed knows your name now 🌀","final boss energy. the score keeps climbing 💀")
        fun of(count: Int): ScrollTier {
            val level=floors.indexOfLast {count.coerceAtLeast(0)>=it}
            return ScrollTier(titles[level],quips[level],level,floors[level],floors.getOrNull(level+1),titles.getOrNull(level+1))
        }
    }
}
object TimeBuckets {
    data class Slice(val day: LocalDate, val millis: Long)
    fun split(start: Long, end: Long, zone: ZoneId): List<Slice> {
        if (end <= start) return emptyList()
        val slices = mutableListOf<Slice>()
        var cursor = start
        while (cursor < end) {
            val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val next = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli().coerceAtMost(end)
            slices += Slice(day, next - cursor)
            cursor = next
        }
        return slices
    }
    fun streak(days: Map<LocalDate, Int>, today: LocalDate, installed: LocalDate): Pair<Int, Int> {
        var previous: LocalDate? = null
        var best = 0; var run = 0
        for(day in days.filter {(date,count)->count>0 && date in installed..today}.keys.sorted()) {
            run=if(previous?.plusDays(1)==day) run+1 else 1
            best=maxOf(best,run);previous=day
        }
        // A streak stays live during today's unplayed day; a whole missed day breaks it.
        val current=if(previous==today || previous==today.minusDays(1)) run else 0
        return current to best
    }
}
