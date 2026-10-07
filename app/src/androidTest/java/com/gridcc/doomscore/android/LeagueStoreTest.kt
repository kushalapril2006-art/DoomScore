package com.gridcc.doomscore.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.core.CounterEvent
import com.gridcc.doomscore.android.core.LeagueRules
import com.gridcc.doomscore.android.core.SourceApp
import com.gridcc.doomscore.android.data.DoomStore
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class LeagueStoreTest {
    @Test fun utcCountsDoNotIncludeAdsOrRewatchesAndCalendarMonthUsesAllDays() {
        assumeTrue("Only the synthetic emulator may reset test history",android.os.Build.MODEL.startsWith("sdk_gphone"))
        val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as DoomApplication
        app.store.preferences.enabled=false
        app.store.clear()
        val now=Instant.now()
        app.store.event(CounterEvent.COUNT,SourceApp.INSTAGRAM,now.toEpochMilli())
        app.store.event(CounterEvent.AD_SKIPPED,SourceApp.INSTAGRAM,now.toEpochMilli())
        app.store.event(CounterEvent.REWATCH_SKIPPED,SourceApp.INSTAGRAM,now.toEpochMilli())
        assertEquals(1,app.store.leagueDays().values.sum())
        assertEquals(LocalDate.now().dayOfMonth,app.store.monthDays().size)
        assertEquals(1,app.store.monthDays().sumOf {it.total})
        val closed=LeagueRules.month(now).minusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        app.store.event(CounterEvent.COUNT,SourceApp.INSTAGRAM,closed.toEpochMilli())
        assertEquals(1,app.store.leagueDays().values.sum())
        app.store.clear()
    }
    @Test fun databaseUpgradeKeepsVersionOneHistoryAndAddsUtcStorage() {
        assumeTrue("Only the synthetic emulator may reset test history",android.os.Build.MODEL.startsWith("sdk_gphone"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val app=context.applicationContext as DoomApplication
        app.store.preferences.enabled=false
        app.store.close()
        context.deleteDatabase("doomscore.db")
        context.openOrCreateDatabase("doomscore.db",0,null).use {db ->
            db.execSQL("CREATE TABLE stats(day TEXT NOT NULL,app TEXT NOT NULL,reels INTEGER NOT NULL DEFAULT 0,watch_ms INTEGER NOT NULL DEFAULT 0,ads INTEGER NOT NULL DEFAULT 0,repeats INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(day,app))")
            db.execSQL("CREATE TABLE hourly(day TEXT NOT NULL,hour INTEGER NOT NULL,reels INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(day,hour))")
            db.execSQL("CREATE TABLE recent(app TEXT NOT NULL,fingerprint TEXT NOT NULL,seen_at INTEGER NOT NULL,PRIMARY KEY(app,fingerprint))")
            db.execSQL("INSERT INTO stats(day,app,reels) VALUES(?,?,?)",arrayOf<Any>(LocalDate.now().toString(),"instagram",17))
            db.version=1
        }
        DoomStore(context).use {upgraded ->
            assertEquals(17,upgraded.day().total)
            assertEquals(3,upgraded.readableDatabase.version)
            assertTrue(upgraded.leagueDays().isEmpty())
            upgraded.clear()
        }
    }
}
