package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TimeBucketsTest {
    @Test fun splitsViewingAtLocalMidnight() {
        val zone=ZoneId.of("Asia/Kolkata")
        val start=LocalDateTime.of(2026,10,6,23,59,59).atZone(zone).toInstant().toEpochMilli()
        val slices=TimeBuckets.split(start,start+2000,zone)
        assertEquals(2,slices.size);assertEquals(1000L,slices[0].millis);assertEquals(LocalDate.of(2026,10,7),slices[1].day)
    }
    @Test fun daylightSavingDayUsesActualElapsedTime() {
        val zone=ZoneId.of("America/New_York");val day=LocalDate.of(2026,3,8)
        val start=day.atStartOfDay(zone).toInstant().toEpochMilli();val end=day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        assertEquals(23*3600000L,TimeBuckets.split(start,end,zone).single().millis)
    }
    @Test fun activeScrollingDaysBuildStreaksRegardlessOfVolume() {
        val installed=LocalDate.of(2026,10,1);val today=installed.plusDays(4)
        assertEquals(3 to 3,TimeBuckets.streak(mapOf(installed.plusDays(2) to 101,installed.plusDays(3) to 2001,today to 10000),today,installed))
    }
    @Test fun zeroDaysCannotEarnAStreakAndMissedDaysBreakIt() {
        val day=LocalDate.of(2026,10,1)
        assertEquals(0 to 0,TimeBuckets.streak(emptyMap(),day,day))
        assertEquals(0 to 2,TimeBuckets.streak(mapOf(day to 1,day.plusDays(1) to 1,day.plusDays(2) to 0),day.plusDays(3),day))
    }
    @Test fun yesterdayStaysLiveUntilTodayEndsAndFutureDaysDoNotCount() {
        val day=LocalDate.of(2026,10,1)
        assertEquals(1 to 1,TimeBuckets.streak(mapOf(day to 1,day.plusDays(2) to 100),day.plusDays(1),day))
        assertEquals(0 to 0,TimeBuckets.streak(mapOf(day.minusDays(1) to 5,day.plusDays(1) to 5),day,day))
    }
}
