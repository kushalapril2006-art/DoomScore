package com.gridcc.doomscore.android.core

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

class LeagueRulesTest {
    @Test fun exactRanksStopAt200AndPercentRoundsUp() {
        assertEquals("#200", LeagueRules.rankLabel(200, 1000))
        assertEquals("top 21%", LeagueRules.rankLabel(201, 1000))
        assertEquals("top 100%", LeagueRules.rankLabel(1000, 1000))
        assertEquals("unranked", LeagueRules.rankLabel(null, 0))
    }
    @Test fun spotlightEndsExactlyAtUtcDayEight() {
        assertTrue(LeagueRules.spotlight(Instant.parse("2026-11-07T23:59:59Z")))
        assertFalse(LeagueRules.spotlight(Instant.parse("2026-11-08T00:00:00Z")))
        assertEquals(YearMonth.of(2027, 1), LeagueRules.month(Instant.parse("2027-01-01T00:00:00Z")))
    }
    @Test fun calendarMonthsIncludeLeapDayAndExcludePreviousMonth() {
        assertTrue(LeagueRules.inSeason(LocalDate.of(2028, 2, 29), YearMonth.of(2028, 2)))
        assertFalse(LeagueRules.inSeason(LocalDate.of(2028, 1, 31), YearMonth.of(2028, 2)))
    }
    @Test fun instagramIsOptionalAndCannotInjectLinks() {
        assertEquals("my.name_1", LeagueRules.instagram(" @My.Name_1 "))
        assertEquals("", LeagueRules.instagram(""))
        for (bad in listOf("https://instagram.com/user", "../evil", "two..dots", "evil\nname", "name?x=1", "a".repeat(31))) {
            assertThrows(IllegalArgumentException::class.java) { LeagueRules.instagram(bad) }
        }
    }
}
