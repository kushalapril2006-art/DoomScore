package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TrophyRulesTest {
    private fun local(unique: Int=0, daily: Int=0, streak: Int=0, earned: Map<String,Long> = emptyMap()) = LocalTrophyProgress(unique,daily,streak,earned)
    @Test fun reelThresholdsUnlockOnlyAtTheirOwnBoundary() {
        for((target,trophy) in listOf(100 to Trophy.ONE_MORE,1000 to Trophy.FOR_YOU,10000 to Trophy.FINAL_BOSS)) {
            assertFalse(TrophyRules.cabinet(local(target-1),null).first {it.trophy==trophy}.unlocked)
            assertTrue(TrophyRules.cabinet(local(target),null).first {it.trophy==trophy}.unlocked)
        }
        assertFalse(TrophyRules.cabinet(local(10000,499),null).first {it.trophy==Trophy.BED_ROT}.unlocked)
        assertTrue(TrophyRules.cabinet(local(0,500),null).first {it.trophy==Trophy.BED_ROT}.unlocked)
    }
    @Test fun scrollStreakIgnoresZeroDaysGapsAndFutureDates() {
        val today=LocalDate.of(2026,10,7)
        assertEquals(7,TrophyRules.bestScrollStreak((0..6).associate {today.minusDays(it.toLong()) to 1L},today))
        assertEquals(3,TrophyRules.bestScrollStreak(mapOf(today to 1L,today.minusDays(1) to 1L,today.minusDays(2) to 1L,today.minusDays(3) to 0L,today.minusDays(4) to 99L,today.plusDays(1) to 1L),today))
    }
    @Test fun streakUsesCalendarDatesAcrossLeapDay() {
        val end=LocalDate.of(2028,3,3)
        assertEquals(7,TrophyRules.bestScrollStreak((0..6).associate {end.minusDays(it.toLong()) to 1L},end))
    }
    @Test fun onlineBadgesNeedFinalizedProofAndAConsecutiveWinStreak() {
        assertFalse(TrophyRules.cabinet(local(10000,500,7),null).any {it.unlocked && it.trophy in setOf(Trophy.OUTSCROLLED,Trophy.UNEMPLOYED,Trophy.GRASS)})
        val mixed=TrophyRules.cabinet(local(),TrophyProofs("owner",5,4,false))
        assertTrue(mixed.first {it.trophy==Trophy.OUTSCROLLED}.unlocked)
        assertFalse(mixed.first {it.trophy==Trophy.UNEMPLOYED}.unlocked)
        assertFalse(mixed.first {it.trophy==Trophy.GRASS}.unlocked)
        val won=TrophyRules.cabinet(local(),TrophyProofs("owner",5,5,true))
        assertTrue(won.filter {it.trophy in setOf(Trophy.OUTSCROLLED,Trophy.UNEMPLOYED,Trophy.GRASS)}.all {it.unlocked})
    }
    @Test fun earnedBadgesDoNotDisappearWhenAStreakEnds() {
        assertTrue(TrophyRules.cabinet(local(earned=mapOf(Trophy.CHRONIC.id to 123L)),null).first {it.trophy==Trophy.CHRONIC}.unlocked)
        assertEquals(8,TrophyRules.cabinet(local(),null).size)
    }
}
