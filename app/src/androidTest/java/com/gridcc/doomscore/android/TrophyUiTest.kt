package com.gridcc.doomscore.android

import android.content.Intent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.core.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class TrophyUiTest {
    @Test fun earnedBadgeAppearsFromQualifiedViewsAndOnlineBadgesAreNotInferred() {
        assumeTrue("Reset synthetic app data only",android.os.Build.MODEL.startsWith("sdk_gphone"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val app=context.applicationContext as DoomApplication
        app.store.preferences.apply {enabled=false;onboarding=true}
        app.store.clear()
        val engine=ReelCounterEngine(app.store)
        val now=System.currentTimeMillis()
        for(n in 1..100) {
            val observation=Observation(SourceApp.INSTAGRAM,n.toString(16).padStart(64,'0'))
            engine.observe(observation,n*1000L,now)
            engine.observe(observation,n*1000L+750,now+750)
        }
        assertEquals(100,app.store.trophies().uniqueReels)
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        val flags=android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        val automation=instrumentation.getUiAutomation(flags)
        fun find(node:AccessibilityNodeInfo?,text:String):AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.text?.toString()==text) return node
            for(i in 0 until node.childCount) {find(node.getChild(i),text)?.let {return it}}
            return null
        }
        fun waitFor(text:String):AccessibilityNodeInfo? {
            val deadline=android.os.SystemClock.elapsedRealtime()+15000
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                find(automation.rootInActiveWindow,text)?.let {return it};Thread.sleep(200)
            }
            return null
        }
        // Open via Stats, where the cabinet button is always visible.
        var stats=waitFor("stats")
        while(stats!=null && !stats.isClickable) stats=stats.parent
        assertTrue(stats?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
        var button=waitFor("Brainrot Trophy Cabinet")
        while(button!=null && !button.isClickable) button=button.parent
        assertTrue(button?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
        assertNotNull(waitFor("1 / 8 UNLOCKED"))
        assertNotNull(waitFor("One More Then I Sleep"))
        assertNotNull(waitFor("UNLOCKED ✦"))
        val badges=TrophyRules.cabinet(app.store.trophies(),null)
        assertEquals(1,badges.count {it.unlocked})
        assertFalse(badges.first {it.trophy==Trophy.GRASS}.unlocked)
        assertFalse(badges.first {it.trophy==Trophy.OUTSCROLLED}.unlocked)
        assertNotNull(waitFor("Close"))
        automation.takeScreenshot()?.let {bitmap ->
            try {java.io.File(context.cacheDir,"trophy-earned.png").outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
            finally {bitmap.recycle()}
        }
        app.store.clear()
    }
}
