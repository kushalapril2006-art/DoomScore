package com.gridcc.doomscore.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.tracking.ReelAccessibilityService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CounterIntegrationTest {
    @Test fun countsActualAccessibilityEventsAndSkipsAdsRewatchesAndPanels() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val app=context.applicationContext as DoomApplication
        assertTrue("Enable the service on the test emulator first",ReelAccessibilityService.isEnabled(context))
        assertTrue("Use only the isolated synthetic emulator",android.os.Build.MODEL.startsWith("sdk_gphone"))
        app.store.preferences.enabled=false
        // Instrumentation force-stops its target process. Rebind our service through Android itself.
        val flags=android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES or
            if(android.os.Build.VERSION.SDK_INT>=31) android.app.UiAutomation.FLAG_DONT_USE_ACCESSIBILITY else 0
        val automation=instrumentation.getUiAutomation(flags)
        if(android.os.Build.VERSION.SDK_INT>=29) {
            automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS)
            try {
                val setting=android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                val original=android.provider.Settings.Secure.getString(context.contentResolver,setting).orEmpty()
                val ours=android.content.ComponentName(context,ReelAccessibilityService::class.java)
                val others=original.split(':').filter {android.content.ComponentName.unflattenFromString(it)!=ours}.joinToString(":")
                android.provider.Settings.Secure.putString(context.contentResolver,setting,others)
                Thread.sleep(300)
                android.provider.Settings.Secure.putString(context.contentResolver,setting,original)
            } finally {automation.dropShellPermissionIdentity()}
        }
        val deadline=android.os.SystemClock.elapsedRealtime()+10_000
        while (!ReelAccessibilityService.state.value.connected && android.os.SystemClock.elapsedRealtime()<deadline) Thread.sleep(100)
        assertTrue("Service must actually be connected",ReelAccessibilityService.state.value.connected)
        app.store.clear();app.store.preferences.apply {disclosed=true;onboarding=true;enabled=true;bubble=true;tracked=setOf(com.gridcc.doomscore.android.core.SourceApp.INSTAGRAM)}
        fun open(reel:String) {
            val returning=reel=="A" && app.store.day().total>=2
            context.startActivity(Intent().setClassName("com.instagram.android","com.gridcc.doomscore.fixture.FixtureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("reel",reel).putExtra("busy",reel=="A"))
            if(reel in setOf("A","B","ad")) {
                val until=android.os.SystemClock.elapsedRealtime()+10_000
                fun observed():Boolean=app.store.day().let {day -> when {
                    reel=="ad" -> day.ads>=1
                    returning -> day.repeats>=1
                    reel=="B" -> day.total>=2
                    else -> day.total>=1
                } }
                while(!observed() && android.os.SystemClock.elapsedRealtime()<until) Thread.sleep(100)
                assertTrue("Accessibility observation timed out for $reel: ${ReelAccessibilityService.state.value}",observed())
            }
            Thread.sleep(1600)
        }
        open("A");assertEquals("First organic reel",1,app.store.day().total)
        Thread.sleep(1000);assertEquals("Looping the same reel",1,app.store.day().total)
        open("B");assertEquals("New reel",2,app.store.day().total)
        open("A");assertEquals("Returning to recently counted reel",2,app.store.day().total);assertEquals(1,app.store.day().repeats)
        open("ad");assertEquals("Sponsored item",2,app.store.day().total);assertEquals(1,app.store.day().ads)
        open("comments");assertEquals("Comment panel",2,app.store.day().total)
        open("home");assertEquals("Ordinary feed",2,app.store.day().total)
        assertTrue(app.store.day().watchMs>1000)
        // Resume inside a static feed with no fresh scroll event. Discovery must recover itself.
        app.store.preferences.enabled=false
        Thread.sleep(500)
        context.startActivity(Intent().setClassName("com.instagram.android","com.gridcc.doomscore.fixture.FixtureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("reel","C"))
        Thread.sleep(1000);assertEquals(2,app.store.day().total)
        app.store.preferences.enabled=true
        val resumeDeadline=android.os.SystemClock.elapsedRealtime()+10_000
        while(app.store.day().total<3 && android.os.SystemClock.elapsedRealtime()<resumeDeadline) Thread.sleep(100)
        assertEquals("Resume must discover the already-open reel",3,app.store.day().total)
        assertTrue("Opted-in floating mascot must actually be attached",ReelAccessibilityService.state.value.floatingVisible)
        // Reconnect the OS service while the same static reel remains open.
        automation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            val setting=android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            val original=android.provider.Settings.Secure.getString(context.contentResolver,setting).orEmpty()
            val ours=android.content.ComponentName(context,ReelAccessibilityService::class.java)
            android.provider.Settings.Secure.putString(context.contentResolver,setting,original.split(':').filter {android.content.ComponentName.unflattenFromString(it)!=ours}.joinToString(":"))
            Thread.sleep(700)
            android.provider.Settings.Secure.putString(context.contentResolver,setting,original)
        } finally {automation.dropShellPermissionIdentity()}
        val reconnectDeadline=android.os.SystemClock.elapsedRealtime()+10_000
        while((!ReelAccessibilityService.state.value.floatingVisible || ReelAccessibilityService.state.value.app==null) && android.os.SystemClock.elapsedRealtime()<reconnectDeadline) Thread.sleep(100)
        assertTrue("Reconnected service must discover the static feed",ReelAccessibilityService.state.value.floatingVisible)
        Thread.sleep(1200);assertEquals("Reconnection must not duplicate an existing reel",3,app.store.day().total)
        context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        app.store.preferences.bubble=true
    }
}
