package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test

class ScrollTierTest {
    @Test fun eachRankAdvancesAtItsThresholdAndNeverPenalizesHigherCounts() {
        val thresholds=listOf(0,1,100,500,1000,2500,5000)
        thresholds.forEachIndexed {level,count ->
            assertEquals(level,ScrollTier.of(count).level)
            if(level>0) assertEquals(level-1,ScrollTier.of(count-1).level)
        }
        assertEquals("final boss",ScrollTier.of(Int.MAX_VALUE).title)
        assertNull(ScrollTier.of(Int.MAX_VALUE).next)
    }
    @Test fun sharedMascotColoursChangeExactlyAtRankThresholds() {
        val counts=listOf(0,1,100,500,1000,2500,5000)
        val colours=listOf(0xFF3DE0FF,0xFFC6FF3D,0xFFFFE14D,0xFFFF8A3D,0xFFFF4D5E,0xFFFF4FB3,0xFF8A5CFF)
        counts.forEachIndexed {level,count ->
            assertEquals(colours[level].toInt(),ScrollTier.of(count).argb)
            if(level>0) assertEquals(colours[level-1].toInt(),ScrollTier.of(count-1).argb)
        }
        assertEquals(colours.last().toInt(),ScrollTier.of(Int.MAX_VALUE).argb)
    }
    @Test fun progressMeasuresNextRankAndContinuesAfterTheLastTier() {
        assertEquals(0f,ScrollTier.of(100).progress(100),0f)
        assertEquals(.5f,ScrollTier.of(300).progress(300),0f)
        assertEquals(1f,ScrollTier.of(10000).progress(10000),0f)
        assertEquals(0,ScrollTier.of(-1).level)
    }
}
