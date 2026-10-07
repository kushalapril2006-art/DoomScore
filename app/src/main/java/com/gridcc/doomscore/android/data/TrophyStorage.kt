package com.gridcc.doomscore.android.data

import android.database.sqlite.SQLiteDatabase
import com.gridcc.doomscore.android.core.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Bounded, salted-hash history only. Raw captions and video identifiers never enter this store. */
object TrophyStorage {
    fun create(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS trophy_unique(app TEXT NOT NULL,fingerprint TEXT NOT NULL,PRIMARY KEY(app,fingerprint))")
        db.execSQL("CREATE TABLE IF NOT EXISTS trophy_daily(day TEXT NOT NULL,app TEXT NOT NULL,fingerprint TEXT NOT NULL,PRIMARY KEY(day,app,fingerprint))")
        db.execSQL("CREATE TABLE IF NOT EXISTS trophy_metrics(id INTEGER PRIMARY KEY CHECK(id=1),lifetime INTEGER NOT NULL DEFAULT 0 CHECK(lifetime BETWEEN 0 AND 10000),best_day INTEGER NOT NULL DEFAULT 0 CHECK(best_day BETWEEN 0 AND 500))")
        db.execSQL("INSERT OR IGNORE INTO trophy_metrics(id) VALUES(1)")
        db.execSQL("CREATE TABLE IF NOT EXISTS trophy_awards(id TEXT PRIMARY KEY NOT NULL,earned_at INTEGER NOT NULL)")
    }
    private fun award(db: SQLiteDatabase,trophy: Trophy,wall: Long) {
        db.execSQL("INSERT OR IGNORE INTO trophy_awards(id,earned_at) VALUES(?,?)",arrayOf<Any>(trophy.id,wall))
    }
    private fun insert(db: SQLiteDatabase,sql: String,values: List<String>): Boolean = db.compileStatement(sql).use {statement ->
        values.forEachIndexed {index,value -> statement.bindString(index+1,value)}
        statement.executeInsert()!=-1L
    }
    fun remember(db: SQLiteDatabase,app: SourceApp,fingerprint: String,wall: Long): Boolean {
        require(Regex("[a-f0-9]{64}").matches(fingerprint)) { "Invalid reel hash" }
        val day=Instant.ofEpochMilli(wall).atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val metrics=db.rawQuery("SELECT lifetime,best_day FROM trophy_metrics WHERE id=1",null).use {cursor ->
            check(cursor.moveToFirst());cursor.getInt(0) to cursor.getInt(1)
        }
        var changed=false
        if(metrics.first<10000 && insert(db,"INSERT OR IGNORE INTO trophy_unique(app,fingerprint) VALUES(?,?)",listOf(app.key,fingerprint))) {
            val count=metrics.first+1
            db.execSQL("UPDATE trophy_metrics SET lifetime=? WHERE id=1",arrayOf<Any>(count))
            for(trophy in listOf(Trophy.ONE_MORE,Trophy.FOR_YOU,Trophy.FINAL_BOSS)) if(count>=trophy.target) award(db,trophy,wall)
            // Once every lifetime milestone is earned, identifiers no longer serve a purpose.
            if(count==10000) db.delete("trophy_unique",null,null)
            changed=true
        }
        if(metrics.second<500) {
            // Only today's set is needed; retain the best-day aggregate and permanent award.
            db.delete("trophy_daily","day<>?",arrayOf(day))
            if(insert(db,"INSERT OR IGNORE INTO trophy_daily(day,app,fingerprint) VALUES(?,?,?)",listOf(day,app.key,fingerprint))) {
                val count=db.rawQuery("SELECT COUNT(*) FROM trophy_daily WHERE day=?",arrayOf(day)).use {cursor -> cursor.moveToFirst();cursor.getInt(0) }
                if(count>metrics.second) db.execSQL("UPDATE trophy_metrics SET best_day=? WHERE id=1",arrayOf<Any>(count.coerceAtMost(500)))
                if(count>=500) {award(db,Trophy.BED_ROT,wall);db.delete("trophy_daily",null,null)}
                changed=true
            }
        }
        return changed
    }
    fun snapshot(db: SQLiteDatabase,today: LocalDate=LocalDate.now()): LocalTrophyProgress {
        val metrics=db.rawQuery("SELECT lifetime,best_day FROM trophy_metrics WHERE id=1",null).use {cursor ->
            check(cursor.moveToFirst());cursor.getInt(0) to cursor.getInt(1)
        }
        val days=mutableMapOf<LocalDate,Long>()
        db.rawQuery("SELECT day,SUM(reels) FROM stats GROUP BY day HAVING SUM(reels)>0",null).use {cursor ->
            while(cursor.moveToNext()) runCatching {LocalDate.parse(cursor.getString(0))}.getOrNull()?.let {days[it]=cursor.getLong(1)}
        }
        val streak=TrophyRules.bestScrollStreak(days,today)
        if(streak>=7) award(db,Trophy.CHRONIC,System.currentTimeMillis())
        val earned=mutableMapOf<String,Long>()
        db.rawQuery("SELECT id,earned_at FROM trophy_awards",null).use {cursor ->
            while(cursor.moveToNext()) if(Trophy.entries.any {it.ordinal<=Trophy.CHRONIC.ordinal && it.id==cursor.getString(0)}) earned[cursor.getString(0)]=cursor.getLong(1)
        }
        return LocalTrophyProgress(metrics.first,metrics.second,streak,earned)
    }
    fun clear(db: SQLiteDatabase) {
        for(table in listOf("trophy_unique","trophy_daily","trophy_awards")) db.delete(table,null,null)
        db.execSQL("UPDATE trophy_metrics SET lifetime=0,best_day=0 WHERE id=1")
    }
}
