package com.gridcc.doomscore.android

import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.core.*
import com.gridcc.doomscore.android.data.DoomStore
import com.gridcc.doomscore.android.data.Preferences
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class TrophyStorageTest {
    private fun isolated(action: (DoomStore,android.content.Context) -> Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val testName="trophy-${UUID.randomUUID()}.db"
        val context=object:ContextWrapper(base) {
            override fun getDatabasePath(name:String)=base.getDatabasePath(testName)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?)=base.openOrCreateDatabase(testName,mode,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?,handler:DatabaseErrorHandler?)=base.openOrCreateDatabase(testName,mode,factory,handler)
        }
        try {DoomStore(context).use {action(it,context)}} finally {base.deleteDatabase(testName)}
    }
    private fun hash(n:Int)=n.toString(16).padStart(64,'0')
    @Test fun distinctViewsSurviveRecentExpiryAndReopeningWithoutDoubleCredit() = isolated {store,context ->
        val now=System.currentTimeMillis()
        store.remember(SourceApp.INSTAGRAM,hash(1),now)
        store.remember(SourceApp.INSTAGRAM,hash(1),now+400000)
        assertEquals(1,store.trophies().uniqueReels)
        store.close()
        DoomStore(context).use {reopened ->
            reopened.remember(SourceApp.INSTAGRAM,hash(1),now+800000)
            assertEquals(1,reopened.trophies().uniqueReels)
            reopened.remember(SourceApp.YOUTUBE,hash(1),now+800000)
            assertEquals(2,reopened.trophies().uniqueReels)
        }
    }
    @Test fun milestonesAndDailyUniquenessAreBoundedAndPersistAcrossDays() = isolated {store,_ ->
        val now=System.currentTimeMillis()
        for(n in 1..500) store.remember(SourceApp.INSTAGRAM,hash(n),now)
        val earned=store.trophies()
        assertEquals(500,earned.uniqueReels);assertEquals(500,earned.bestUniqueDay)
        assertTrue(Trophy.ONE_MORE.id in earned.earned);assertTrue(Trophy.BED_ROT.id in earned.earned)
        assertFalse(Trophy.FOR_YOU.id in earned.earned)
        val stamp=earned.earned[Trophy.ONE_MORE.id]
        store.remember(SourceApp.INSTAGRAM,hash(1),now+86400000)
        assertEquals(stamp,store.trophies().earned[Trophy.ONE_MORE.id])
        assertEquals(500,store.trophies().bestUniqueDay)
        store.readableDatabase.rawQuery("SELECT COUNT(*) FROM trophy_daily",null).use {c ->c.moveToFirst();assertEquals(0,c.getInt(0))}
        store.clear();assertEquals(0,store.trophies().uniqueReels);assertTrue(store.trophies().earned.isEmpty())
    }
    @Test fun oldCountsCreditActualScrollingStreakButNotInventedUniqueViews() = isolated {store,_ ->
        val today=LocalDate.now()
        for(n in 0..6) store.event(CounterEvent.COUNT,SourceApp.INSTAGRAM,today.minusDays(n.toLong()).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        val snapshot=store.trophies()
        assertEquals(7,snapshot.bestScrollStreak);assertTrue(Trophy.CHRONIC.id in snapshot.earned)
        assertEquals(0,snapshot.uniqueReels)
    }
    @Test fun highestLifetimeAwardPurgesHashesAndDailyRewatchesStayUnique() = isolated {store,_ ->
        val now=System.currentTimeMillis()
        store.writableDatabase.execSQL("UPDATE trophy_metrics SET lifetime=9999,best_day=500 WHERE id=1")
        store.remember(SourceApp.INSTAGRAM,hash(1),now)
        assertTrue(Trophy.FINAL_BOSS.id in store.trophies().earned)
        store.readableDatabase.rawQuery("SELECT COUNT(*) FROM trophy_unique",null).use {c ->c.moveToFirst();assertEquals(0,c.getInt(0))}
        store.remember(SourceApp.INSTAGRAM,hash(2),now)
        assertEquals(10000,store.trophies().uniqueReels)
    }
    @Test fun personalBestUsesAllDaysAndHighCountsKeepTheScrollingStreak() = isolated {store,_ ->
        org.junit.Assume.assumeTrue("Synthetic emulator only",android.os.Build.MODEL.startsWith("sdk_gphone"))
        val today=LocalDate.now()
        val yesterday=today.minusDays(1)
        val settings=InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("settings",android.content.Context.MODE_PRIVATE)
        val original=settings.getString("installed",null)
        settings.edit().putString("installed",yesterday.toString()).commit()
        try {
            store.writableDatabase.execSQL("INSERT INTO stats(day,app,reels) VALUES(?,?,?)",arrayOf<Any>(yesterday.toString(),"instagram",10000))
            store.writableDatabase.execSQL("INSERT INTO stats(day,app,reels) VALUES(?,?,?)",arrayOf<Any>(today.toString(),"instagram",1000))
            store.writableDatabase.execSQL("INSERT INTO stats(day,app,reels) VALUES(?,?,?)",arrayOf<Any>(today.toString(),"youtube",1500))
            assertEquals(10000,store.personalBest())
            assertEquals(2 to 2,store.streak())
            assertEquals(2500,store.day().total)
            store.clear();assertEquals(0,store.personalBest());assertEquals(0 to 0,store.streak())
        } finally {
            if(original==null) settings.edit().remove("installed").commit() else settings.edit().putString("installed",original).commit()
        }
    }
    @Test fun batchedYearHistoryPreservesCountsHoursAndEmptyDays() = isolated {store,_ ->
        val today=LocalDate.now()
        val past=today.minusDays(364)
        store.writableDatabase.execSQL("INSERT INTO stats(day,app,reels,watch_ms,ads,repeats) VALUES(?,?,?,?,?,?)",arrayOf<Any>(past.toString(),"instagram",10,60000,2,3))
        store.writableDatabase.execSQL("INSERT INTO hourly(day,hour,reels) VALUES(?,?,?)",arrayOf<Any>(past.toString(),23,10))
        store.writableDatabase.execSQL("INSERT INTO stats(day,app,reels) VALUES(?,?,?)",arrayOf<Any>(today.toString(),"youtube",20))
        val history=store.days(365)
        assertEquals(365,history.size);assertEquals(past,history.first().day);assertEquals(today,history.last().day)
        assertEquals(10,history.first().total);assertEquals(60000L,history.first().watchMs)
        assertEquals(2,history.first().ads);assertEquals(3,history.first().repeats);assertEquals(10,history.first().hourly[23])
        assertEquals(0,history[1].total);assertEquals(20,history.last().total)
        assertEquals(20L,store.monthTotal())
        assertEquals(30,history.sumOf {it.total})
    }
    @Test fun retiredReminderSettingsCannotReactivateWithOptionalLiveCounter() {
        org.junit.Assume.assumeTrue("Synthetic emulator only",android.os.Build.MODEL.startsWith("sdk_gphone"))
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val settings=context.getSharedPreferences("settings",android.content.Context.MODE_PRIVATE)
        val consent=settings.getBoolean("disclosed",false)
        settings.edit().putInt("goal",5).putBoolean("nudges",true).commit()
        Preferences(context)
        assertFalse(settings.contains("goal"));assertFalse(settings.contains("nudges"))
        assertEquals(consent,settings.getBoolean("disclosed",false))
        val permissions=context.packageManager.getPackageInfo(context.packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("android.permission.POST_NOTIFICATIONS" in permissions)
        assertFalse(Preferences(context).liveNotification)
        assertNull((context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).getNotificationChannel("cap"))
    }
}
