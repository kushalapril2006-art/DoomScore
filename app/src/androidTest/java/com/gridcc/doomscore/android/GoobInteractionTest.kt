package com.gridcc.doomscore.android

import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class GoobInteractionTest {
    @Test fun tapReactAndReducedMotionDoNotChangeReelTotals() {
        check(android.os.Build.MODEL.startsWith("sdk_gphone")) {"Synthetic emulator only"}
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val app=context.applicationContext as DoomApplication
        check(!app.league.configured && !app.firebase.configured) {"Offline preview build required"}
        app.store.preferences.apply {enabled=false;onboarding=true}
        val before=app.store.day().total
        val automation=instrumentation.uiAutomation
        fun find(node:AccessibilityNodeInfo?):AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.contentDescription?.toString()?.startsWith("Goob ")==true && node.isClickable) return node
            for(i in 0 until node.childCount) {find(node.getChild(i))?.let {return it}}
            return null
        }
        fun goob():AccessibilityNodeInfo {
            val deadline=android.os.SystemClock.elapsedRealtime()+15000
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                find(automation.rootInActiveWindow)?.let {return it};Thread.sleep(100)
            }
            error("Clickable Goob unavailable")
        }
        fun shell(command:String) {ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {it.readBytes()}}
        fun waitForRest() {
            val deadline=android.os.SystemClock.elapsedRealtime()+8000
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                if(goob().contentDescription.toString().startsWith("Goob looks ")) return
                Thread.sleep(100)
            }
            error("Goob reaction did not finish")
        }
        fun waitForGreeting() {
            val deadline=android.os.SystemClock.elapsedRealtime()+2500
            while(android.os.SystemClock.elapsedRealtime()<deadline) {
                if(goob().contentDescription.toString()=="Goob says hi") return
                Thread.sleep(25)
            }
            error("Goob did not greet after a tap")
        }
        val scale=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        try {
            assertTrue(goob().performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitForGreeting()
            waitForRest()
            assertEquals(before,app.store.day().total)
            shell("settings put global animator_duration_scale 0")
            Thread.sleep(1200)
            assertTrue(goob().performAction(AccessibilityNodeInfo.ACTION_CLICK))
            waitForGreeting()
            waitForRest()
            assertEquals(before,app.store.day().total)
        } finally {shell("settings put global animator_duration_scale $scale")}
    }
}
