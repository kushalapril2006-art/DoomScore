package com.gridcc.doomscore.android.core

import java.time.LocalDate

enum class Trophy(val id: String, val title: String, val rule: String, val glyph: String, val target: Int) {
    ONE_MORE("one_more", "One More Then I Sleep", "100 unique reels", "🌙", 100),
    FOR_YOU("for_you", "For You? For Me.", "1,000 unique reels", "🌀", 1000),
    FINAL_BOSS("final_boss", "Final Boss of Brainrot", "10,000 unique reels", "💀", 10000),
    BED_ROT("bed_rot", "Bed Rot Any%", "500 unique reels in one day", "🛏", 500),
    CHRONIC("chronic", "Chronically Online", "Scroll 7 days in a row", "🌐", 7),
    OUTSCROLLED("outscrolled", "Bro Got Outscrolled", "Win your first Battle", "⚔", 1),
    UNEMPLOYED("unemployed", "Unemployed Behaviour", "Win 5 Battles in a row", "🏅", 5),
    GRASS("grass", "Touch Grass Is a Threat", "Reach the global top 10", "🌱", 1)
}

data class LocalTrophyProgress(val uniqueReels: Int, val bestUniqueDay: Int, val bestScrollStreak: Int, val earned: Map<String, Long>)
data class TrophyProofs(val owner: String, val battleWins: Int, val bestWinStreak: Int, val globalTop10: Boolean)
data class TrophyProgress(val trophy: Trophy, val progress: Int, val unlocked: Boolean, val earnedAt: Long? = null)

object TrophyRules {
    fun bestScrollStreak(days: Map<LocalDate, Long>, today: LocalDate): Int {
        var previous: LocalDate? = null
        var run=0; var best=0
        for(day in days.filter { (date,count) -> count>0 && date<=today }.keys.sorted()) {
            run=if(previous?.plusDays(1)==day) run+1 else 1
            best=maxOf(best,run);previous=day
        }
        return best.coerceAtMost(7)
    }
    fun cabinet(local: LocalTrophyProgress, verified: TrophyProofs?): List<TrophyProgress> = Trophy.entries.map { trophy ->
        val value=when(trophy) {
            Trophy.ONE_MORE, Trophy.FOR_YOU, Trophy.FINAL_BOSS -> local.uniqueReels
            Trophy.BED_ROT -> local.bestUniqueDay
            Trophy.CHRONIC -> local.bestScrollStreak
            Trophy.OUTSCROLLED -> verified?.battleWins ?: 0
            Trophy.UNEMPLOYED -> verified?.bestWinStreak ?: 0
            Trophy.GRASS -> if(verified?.globalTop10==true) 1 else 0
        }.coerceIn(0,trophy.target)
        val date=if(trophy.ordinal<=Trophy.CHRONIC.ordinal) local.earned[trophy.id] else null
        TrophyProgress(trophy,value,date!=null || value>=trophy.target,date)
    }
}
