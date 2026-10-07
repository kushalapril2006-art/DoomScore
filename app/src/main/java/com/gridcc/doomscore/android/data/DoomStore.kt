package com.gridcc.doomscore.android.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gridcc.doomscore.android.core.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.YearMonth
import java.util.UUID

class Preferences(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    init {prefs.edit().remove("goal").remove("nudges").apply()}
    val revisions = MutableStateFlow(0)
    private fun changed() { revisions.value++ }
    private fun boolean(name: String, fallback: Boolean) = runCatching { prefs.getBoolean(name, fallback) }.getOrDefault(false)
    var disclosed: Boolean
        get() = boolean("disclosed", false)
        set(value) { prefs.edit().putBoolean("disclosed", value).apply(); changed() }
    var onboarding: Boolean
        get() = boolean("onboarding", false)
        set(value) { prefs.edit().putBoolean("onboarding", value).apply(); changed() }
    var enabled: Boolean
        get() = boolean("enabled", true)
        set(value) { prefs.edit().putBoolean("enabled", value).apply(); changed() }
    var bubble: Boolean
        get() = boolean("bubble", true)
        set(value) { prefs.edit().putBoolean("bubble", value).apply(); changed() }
    var island: Boolean
        get() = boolean("island", false)
        set(value) { prefs.edit().putBoolean("island", value).apply(); changed() }
    var liveNotification: Boolean
        get() = boolean("live_notification", false)
        set(value) { prefs.edit().putBoolean("live_notification", value).apply(); changed() }
    var tracked: Set<SourceApp>
        get() = runCatching { SourceApp.entries.filter { it.key in (prefs.getStringSet("tracked", setOf("instagram", "youtube")) ?: emptySet()) }.toSet() }.getOrDefault(emptySet())
        set(value) { prefs.edit().putStringSet("tracked", value.map { it.key }.toSet()).apply(); changed() }
    val salt: String get() = runCatching { InputRules.uuid(prefs.getString("salt", null).orEmpty()) }.getOrNull()
        ?: UUID.randomUUID().toString().also { prefs.edit().putString("salt", it).commit() }
    val installed: LocalDate get() = runCatching { LocalDate.parse(prefs.getString("installed", null)).takeIf { !it.isAfter(LocalDate.now()) } }.getOrNull()
        ?: LocalDate.now().also { prefs.edit().putString("installed", it.toString()).commit() }
}

class DoomStore(context: Context) : SQLiteOpenHelper(context, "doomscore.db", null, 3), CounterSink {
    val changes = MutableStateFlow(0)
    val preferences = Preferences(context)
    init { preferences.installed; preferences.salt }
    override fun onCreate(db: SQLiteDatabase) {
        TrophyStorage.create(db)
        createLeagueTable(db)
        db.execSQL("CREATE TABLE stats(day TEXT NOT NULL, app TEXT NOT NULL, reels INTEGER NOT NULL DEFAULT 0, watch_ms INTEGER NOT NULL DEFAULT 0, ads INTEGER NOT NULL DEFAULT 0, repeats INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(day,app))")
        db.execSQL("CREATE TABLE hourly(day TEXT NOT NULL, hour INTEGER NOT NULL, reels INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(day,hour))")
        db.execSQL("CREATE TABLE recent(app TEXT NOT NULL, fingerprint TEXT NOT NULL, seen_at INTEGER NOT NULL, PRIMARY KEY(app,fingerprint))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion < 2) createLeagueTable(db)
        if(oldVersion < 3) TrophyStorage.create(db)
    }
    private fun createLeagueTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS league_utc(day TEXT PRIMARY KEY NOT NULL, reels INTEGER NOT NULL DEFAULT 0)")
    }
    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if(!db.isReadOnly) pruneRecent(db,System.currentTimeMillis())
    }
    private var lastPrune=0L
    private fun pruneRecent(db:SQLiteDatabase,wall:Long) {
        db.delete("recent", "seen_at < ? OR seen_at > ?", arrayOf((wall - 300_000).toString(), wall.toString()))
        lastPrune=wall
    }
    @Synchronized override fun recentlySeen(app: SourceApp, fingerprint: String, wall: Long): Boolean {
        pruneRecent(writableDatabase,wall)
        return readableDatabase.rawQuery("SELECT 1 FROM recent WHERE app=? AND fingerprint=?", arrayOf(app.key, fingerprint)).use { it.moveToFirst() }
    }
    @Synchronized override fun remember(app: SourceApp, fingerprint: String, wall: Long) {
        if(wall-lastPrune !in 0..59_999) pruneRecent(writableDatabase,wall)
        val db=writableDatabase
        db.beginTransaction()
        var changed=false
        try {
            db.execSQL("INSERT OR REPLACE INTO recent(app,fingerprint,seen_at) VALUES(?,?,?)", arrayOf<Any>(app.key, fingerprint, wall))
            changed=TrophyStorage.remember(db,app,fingerprint,wall)
            db.setTransactionSuccessful()
        } finally {db.endTransaction()}
        if(changed) changes.value++
    }
    @Synchronized override fun event(kind: CounterEvent, app: SourceApp, wall: Long) {
        val date = Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault())
        val day = date.toLocalDate().toString()
        val column = when (kind) { CounterEvent.COUNT -> "reels"; CounterEvent.AD_SKIPPED -> "ads"; CounterEvent.REWATCH_SKIPPED -> "repeats" }
        val db = writableDatabase
        db.beginTransaction()
        try {
            ensureRow(db, day, app)
            db.execSQL("UPDATE stats SET $column=$column+1 WHERE day=? AND app=?", arrayOf(day, app.key))
            if (kind == CounterEvent.COUNT) {
                val utcDay = Instant.ofEpochMilli(wall).atOffset(ZoneOffset.UTC).toLocalDate().toString()
                db.execSQL("INSERT OR IGNORE INTO league_utc(day) VALUES(?)", arrayOf(utcDay))
                db.execSQL("UPDATE league_utc SET reels=reels+1 WHERE day=?", arrayOf(utcDay))
                db.execSQL("INSERT OR IGNORE INTO hourly(day,hour) VALUES(?,?)", arrayOf<Any>(day, date.hour))
                db.execSQL("UPDATE hourly SET reels=reels+1 WHERE day=? AND hour=?", arrayOf<Any>(day, date.hour))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        changes.value++
    }
    @Synchronized override fun watch(app: SourceApp, from: Long, to: Long) {
        if (to <= from) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            TimeBuckets.split(from, to, ZoneId.systemDefault()).forEach { slice ->
                ensureRow(db, slice.day.toString(), app)
                db.execSQL("UPDATE stats SET watch_ms=watch_ms+? WHERE day=? AND app=?", arrayOf<Any>(slice.millis, slice.day.toString(), app.key))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        changes.value++
    }
    private fun ensureRow(db: SQLiteDatabase, day: String, app: SourceApp) {
        db.execSQL("INSERT OR IGNORE INTO stats(day,app) VALUES(?,?)", arrayOf(day, app.key))
    }
    @Synchronized fun day(day: LocalDate = LocalDate.now()): DayStats {
        val stats = mutableMapOf<SourceApp, AppStats>()
        readableDatabase.rawQuery("SELECT app,reels,watch_ms,ads,repeats FROM stats WHERE day=?", arrayOf(day.toString())).use { c ->
            while (c.moveToNext()) SourceApp.entries.firstOrNull { it.key == c.getString(0) }?.let { stats[it] = AppStats(c.getInt(1), c.getLong(2), c.getInt(3), c.getInt(4)) }
        }
        val hours = MutableList(24) { 0 }
        readableDatabase.rawQuery("SELECT hour,reels FROM hourly WHERE day=?", arrayOf(day.toString())).use { c -> while (c.moveToNext()) hours[c.getInt(0)] = c.getInt(1) }
        return DayStats(day, stats, hours)
    }
    @Synchronized fun days(count: Int): List<DayStats> {
        require(count in 1..366)
        val today=LocalDate.now()
        val dates=(count-1 downTo 0).map {today.minusDays(it.toLong())}
        val bounds=arrayOf(dates.first().toString(),today.toString())
        val apps=mutableMapOf<LocalDate,MutableMap<SourceApp,AppStats>>()
        val hours=mutableMapOf<LocalDate,MutableList<Int>>()
        // Two indexed range reads replace two queries for every displayed day.
        readableDatabase.rawQuery("SELECT day,app,reels,watch_ms,ads,repeats FROM stats WHERE day>=? AND day<=? ORDER BY day",bounds).use {c ->
            while(c.moveToNext()) SourceApp.entries.firstOrNull {it.key==c.getString(1)}?.let {source ->
                apps.getOrPut(LocalDate.parse(c.getString(0))) {mutableMapOf()}[source]=AppStats(c.getInt(2),c.getLong(3),c.getInt(4),c.getInt(5))
            }
        }
        readableDatabase.rawQuery("SELECT day,hour,reels FROM hourly WHERE day>=? AND day<=? ORDER BY day,hour",bounds).use {c ->
            while(c.moveToNext()) hours.getOrPut(LocalDate.parse(c.getString(0))) {MutableList(24){0}}[c.getInt(1)]=c.getInt(2)
        }
        return dates.map {DayStats(it,apps[it].orEmpty(),hours[it] ?: List(24){0})}
    }
    @Synchronized fun monthTotal(): Long {
        val month=YearMonth.now()
        return readableDatabase.rawQuery("SELECT COALESCE(SUM(reels),0) FROM stats WHERE day>=? AND day<?",arrayOf(month.atDay(1).toString(),month.plusMonths(1).atDay(1).toString())).use {c ->c.moveToFirst();c.getLong(0)}
    }
    @Synchronized fun monthDays(): List<DayStats> = days(LocalDate.now().dayOfMonth)
    @Synchronized fun trophies(): LocalTrophyProgress = TrophyStorage.snapshot(writableDatabase)
    @Synchronized fun leagueDays(): Map<LocalDate, Int> {
        val month = com.gridcc.doomscore.android.core.LeagueRules.month()
        val result = linkedMapOf<LocalDate, Int>()
        readableDatabase.rawQuery("SELECT day,reels FROM league_utc WHERE day>=? AND day<? ORDER BY day", arrayOf(month.atDay(1).toString(), month.plusMonths(1).atDay(1).toString())).use { c ->
            while(c.moveToNext()) result[LocalDate.parse(c.getString(0))] = c.getInt(1)
        }
        return result
    }
    @Synchronized fun streak(): Pair<Int, Int> {
        val totals = mutableMapOf<LocalDate, Int>()
        readableDatabase.rawQuery("SELECT day,SUM(reels) FROM stats GROUP BY day", null).use { c -> while (c.moveToNext()) totals[LocalDate.parse(c.getString(0))] = c.getInt(1) }
        return TimeBuckets.streak(totals, LocalDate.now(), preferences.installed)
    }
    @Synchronized fun personalBest(): Int = readableDatabase.rawQuery("SELECT MAX(total) FROM (SELECT SUM(reels) AS total FROM stats GROUP BY day)",null).use {c ->c.moveToFirst();c.getInt(0)}
    @Synchronized fun clear() {
        val db = writableDatabase; db.beginTransaction()
        try { listOf("stats", "hourly", "recent", "league_utc").forEach { db.delete(it, null, null) }; TrophyStorage.clear(db); db.setTransactionSuccessful() }
        finally { db.endTransaction() }
        changes.value++
    }
}
