package com.gridcc.doomscore.android

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.core.SourceApp
import com.gridcc.doomscore.android.data.DoomStore
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import java.util.UUID

class RetentionTest {
    @Test fun openingTheAppRemovesExpiredHashesAndRetainsFreshOnes() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val testName="retention-${UUID.randomUUID()}.db"
        val isolated=object:ContextWrapper(base) {
            override fun getDatabasePath(name:String)=base.getDatabasePath(testName)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?):SQLiteDatabase=base.openOrCreateDatabase(testName,mode,factory)
            override fun openOrCreateDatabase(name:String,mode:Int,factory:SQLiteDatabase.CursorFactory?,errorHandler:DatabaseErrorHandler?):SQLiteDatabase=base.openOrCreateDatabase(testName,mode,factory,errorHandler)
        }
        val now=System.currentTimeMillis()
        try {
            DoomStore(isolated).use {store ->
                val db=store.writableDatabase
                for((name,seen) in listOf("old" to now-400_000,"fresh" to now,"future" to now+400_000)) {
                    db.execSQL("INSERT INTO recent(app,fingerprint,seen_at) VALUES(?,?,?)",arrayOf<Any>(SourceApp.INSTAGRAM.key,name,seen))
                }
            }
            DoomStore(isolated).use {store ->
                store.readableDatabase.rawQuery("SELECT fingerprint FROM recent ORDER BY fingerprint",null).use {cursor ->
                    assertEquals(1,cursor.count);assertTrue(cursor.moveToFirst());assertEquals("fresh",cursor.getString(0))
                }
            }
        } finally {base.deleteDatabase(testName)}
    }
}
