package com.gridcc.doomscore.android

import android.content.Intent
import android.graphics.Bitmap
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.core.LeagueRules
import com.gridcc.doomscore.android.network.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.Instant

/** Synthetic standings only; deliberately rejects a configured live backend. */
class LeagueLayoutTest {
    @Test fun standingsKeepPositionAndAccountAccessible() {
        check(android.os.Build.MODEL.startsWith("sdk_gphone")) {"Synthetic emulator only"}
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val app=context.applicationContext as DoomApplication
        check(!app.league.configured && !app.firebase.configured) {"Offline preview build required"}
        val automation=instrumentation.uiAutomation
        fun find(node: AccessibilityNodeInfo?,text: String): AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.text?.toString()==text) return node
            for(i in 0 until node.childCount) {find(node.getChild(i),text)?.let {return it}}
            return null
        }
        fun waitFor(text: String): AccessibilityNodeInfo {
            val deadline=android.os.SystemClock.elapsedRealtime()+15000
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                find(automation.rootInActiveWindow,text)?.let {return it}
                Thread.sleep(150)
            }
            error("Missing UI: $text")
        }
        fun tap(text: String) {
            var node=waitFor(text)
            while(!node.isClickable) node=checkNotNull(node.parent)
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            Thread.sleep(400)
        }
        fun capture(name: String) {
            val bitmap=checkNotNull(automation.takeScreenshot())
            try {File(context.cacheDir,name).outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
            finally {bitmap.recycle()}
        }
        app.store.preferences.apply {enabled=false;onboarding=true}
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        waitFor("TODAY'S SCORE")
        tap("league")
        waitFor("global leaderboard")
        val month=LeagueRules.month()
        val rows=(1..53).map {rank->LeagueRow(rank,if(rank<=3) "scroller.with.long${rank}" else "scroller.$rank",if(rank==1) "sample.champion" else "","",100_000L-rank*1000)}
        val board=LeagueBoard(month,300,rows,null,80,125,rows.take(3),month.minusMonths(1))
        app.league.state.value=LeagueState(profile=LeagueProfile("my.scroll.identity","","",true,true),board=board)
        waitFor("@scroller.with.long1")
        waitFor("IG @sample.champion ↗")
        waitFor("YOUR POSITION")
        waitFor("top 80%")
        assertNull(find(automation.rootInActiveWindow,"Sign in with Google"))
        if(LeagueRules.spotlight(Instant.now())) waitFor("LAST MONTH'S WINNERS")
        else assertNull(find(automation.rootInActiveWindow,"LAST MONTH'S WINNERS"))
        capture("league-sports-table.png")
        fun scrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.isScrollable) return node
            for(i in 0 until node.childCount) {scrollable(node.getChild(i))?.let {return it}}
            return null
        }
        repeat(8) {
            scrollable(automation.rootInActiveWindow)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            Thread.sleep(200)
        }
        waitFor("YOUR POSITION")
        waitFor("top 80%")
        tap("Account")
        waitFor("League account")
        waitFor("Edit profile")
        tap("Close")
        app.league.state.value=app.league.state.value.copy(board=board.copy(myRank=42,myPercent=null))
        waitFor("#42")
        capture("league-pinned-position.png")
        app.league.state.value=LeagueState()
    }
}
