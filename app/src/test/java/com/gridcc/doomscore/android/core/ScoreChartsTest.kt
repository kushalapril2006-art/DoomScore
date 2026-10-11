package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScoreChartsTest {
    private fun day(date:String,n:Int)=DayStats(LocalDate.parse(date),mapOf(SourceApp.INSTAGRAM to AppStats(count=n)))
    @Test fun oldDaysEmptyDaysAndMonthEdgesHaveTheirOwnCounts() {
        val days=listOf(day("2025-12-31",9),day("2026-01-01",0),day("2026-01-02",7))
        assertEquals(listOf(9L,0L,7L),ScoreCharts.daily(days).map {it.reels})
        assertEquals(listOf(9L,7L),ScoreCharts.monthly(days).map {it.reels})
        assertTrue(ScoreCharts.monthly(days)[0].label.contains("2025"))
        assertTrue(ScoreCharts.monthly(days)[1].label.contains("2026"))
    }
    @Test fun hourlyReadoutUsesExactDayAndHourRatherThanTodaysCaption() {
        val day=day("2024-02-29",3).copy(hourly=List(24) {if(it==23) 3 else 0})
        val bars=ScoreCharts.hourly(day)
        assertEquals(24,bars.size);assertEquals(3L,bars[23].reels)
        assertTrue(bars[23].label.contains("2024"));assertTrue(bars[23].label.contains("23:00–23:59"))
        assertEquals(0L,bars[0].reels)
    }
    @Test fun draggingToEitherEdgeClampsAndBadGeometryDoesNotSelect() {
        assertEquals(0,ScoreCharts.indexAt(-1f,700f,7))
        assertEquals(1,ScoreCharts.indexAt(100f,700f,7))
        assertEquals(6,ScoreCharts.indexAt(700f,700f,7))
        assertEquals(6,ScoreCharts.indexAt(900f,700f,7))
        assertNull(ScoreCharts.indexAt(0f,0f,7));assertNull(ScoreCharts.indexAt(Float.NaN,700f,7));assertNull(ScoreCharts.indexAt(5f,700f,0))
    }
    @Test fun aggregateHourReadoutPreservesTheSelectedRangeAndLargeCounts() {
        val days=listOf(day("2026-01-01",0),day("2026-01-02",0)).map {it.copy(hourly=List(24) {hour->if(hour==12) Int.MAX_VALUE else 0})}
        val bars=ScoreCharts.hoursAcross(days,"1–2 Jan")
        assertEquals(2L*Int.MAX_VALUE,bars[12].reels);assertTrue(bars[12].label.startsWith("1–2 Jan"))
        assertTrue(ScoreCharts.monthly(emptyList()).isEmpty())
    }
}
