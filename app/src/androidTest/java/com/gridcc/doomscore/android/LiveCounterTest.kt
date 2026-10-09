package com.gridcc.doomscore.android

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.core.ScrollTier
import com.gridcc.doomscore.android.core.SourceApp
import com.gridcc.doomscore.android.data.Preferences
import com.gridcc.doomscore.android.tracking.GoobIcon
import com.gridcc.doomscore.android.tracking.LiveCounterNotification
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.After
import java.util.UUID

class LiveCounterTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun deviceOnly() {assertTrue("Synthetic emulator only",Build.MODEL.startsWith("sdk_gphone"))}
    private var previousEnabled=false
    private var previousLive=false
    private var isolated=false
    @Before fun isolatePublisherFromTheRunningAccessibilityCounter() {
        deviceOnly()
        val prefs=(context.applicationContext as DoomApplication).store.preferences
        previousEnabled=prefs.enabled;previousLive=prefs.liveNotification
        isolated=true
        prefs.enabled=false;prefs.liveNotification=false
        instrumentation.waitForIdleSync();Thread.sleep(300)
        LiveCounterNotification(context).clear(force=true)
    }
    @After fun restoreCounterPreferences() {
        if(!isolated) return
        LiveCounterNotification(context).clear(force=true)
        val prefs=(context.applicationContext as DoomApplication).store.preferences
        prefs.liveNotification=previousLive;prefs.enabled=previousEnabled
    }
    @Test fun optionalOutputsStartOffAndDoNotChangeConsentOrPauseState() {
        deviceOnly()
        val name="live-test-${UUID.randomUUID()}"
        val isolated=object:ContextWrapper(context) {
            override fun getSharedPreferences(ignored:String,mode:Int)=context.getSharedPreferences(name,mode)
        }
        try {
            val prefs=Preferences(isolated)
            assertFalse(prefs.island);assertFalse(prefs.liveNotification);assertFalse(prefs.disclosed)
            prefs.enabled=false;prefs.island=true;prefs.liveNotification=true
            assertFalse(prefs.enabled);assertFalse(prefs.disclosed)
            assertTrue(Preferences(isolated).island);assertTrue(Preferences(isolated).liveNotification)
        } finally {context.deleteSharedPreferences(name)}
    }
    @Test fun nativeGoobChangesColourAndKeepsTransparentCorners() {
        deviceOnly()
        val icons=listOf(0,1,100,500,1000,2500,5000).map {GoobIcon.bitmap(ScrollTier.of(it))}
        try {
            icons.forEach {assertEquals(0,it.getPixel(0,0));assertTrue(android.graphics.Color.alpha(it.getPixel(48,32))>0)}
            assertEquals(7,icons.map {it.getPixel(48,70)}.distinct().size)
            val dir=java.io.File(context.cacheDir,"island-icons").apply {mkdirs()}
            icons.forEachIndexed {index,b ->java.io.File(dir,"goob-$index.png").outputStream().use {b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
        } finally {icons.forEach {it.recycle()}}
    }
    @Test fun disabledOrFinishedSessionNeverPosts() {
        deviceOnly()
        val publisher=LiveCounterNotification(context)
        val manager=context.getSystemService(NotificationManager::class.java)
        publisher.clear()
        publisher.update(SourceApp.INSTAGRAM,500,false)
        assertFalse(manager.activeNotifications.any {it.id==LiveCounterNotification.ID})
        publisher.update(null,500,true)
        assertFalse(manager.activeNotifications.any {it.id==LiveCounterNotification.ID})
    }
    @Test fun activeNotificationUpdatesSilentlyMasksLockScreenAndCancelsAfterSession() {
        deviceOnly()
        val old=context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
        val automation=instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        fun shell(command:String) {automation.executeShellCommand(command).use {fd->java.io.FileInputStream(fd.fileDescriptor).use {it.readBytes()}};Thread.sleep(300)}
        val publisher=LiveCounterNotification(context)
        val manager=context.getSystemService(NotificationManager::class.java)
        fun published(title:String):android.service.notification.StatusBarNotification {
            val until=android.os.SystemClock.elapsedRealtime()+5000
            do {
                manager.activeNotifications.firstOrNull {it.id==LiveCounterNotification.ID && it.notification.extras.getString(Notification.EXTRA_TITLE)==title}?.let {return it}
                Thread.sleep(100)
            } while(android.os.SystemClock.elapsedRealtime()<until)
            throw AssertionError("Live counter did not publish $title")
        }
        try {
            if(Build.VERSION.SDK_INT>=33) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
            publisher.update(SourceApp.INSTAGRAM,99,true);Thread.sleep(300)
            val first=published("99 reels today")
            assertEquals("99 reels today",first.notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(Notification.VISIBILITY_PRIVATE,first.notification.visibility)
            assertNotNull(first.notification.getLargeIcon())
            assertFalse(first.notification.publicVersion.extras.getString(Notification.EXTRA_TITLE).orEmpty().contains("99"))
            assertTrue(first.notification.flags and Notification.FLAG_ONGOING_EVENT!=0)
            if(Build.VERSION.SDK_INT>=36) {
                assertTrue(first.notification.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
                assertEquals("99",first.notification.extras.getString("android.shortCriticalText"))
                assertTrue(first.notification.hasPromotableCharacteristics())
            }
            val channel=manager.getNotificationChannel(LiveCounterNotification.CHANNEL)
            assertEquals(NotificationManager.IMPORTANCE_LOW,channel.importance);assertNull(channel.sound);assertFalse(channel.shouldVibrate())
            publisher.update(SourceApp.INSTAGRAM,99,true);Thread.sleep(200)
            assertEquals(first.postTime,published("99 reels today").postTime)
            manager.cancel(LiveCounterNotification.ID)
            Thread.sleep(3200)
            publisher.update(SourceApp.INSTAGRAM,99,true);Thread.sleep(300)
            published("99 reels today")
            publisher.update(SourceApp.YOUTUBE,1000,true);Thread.sleep(300)
            val next=published("1000 reels today").notification
            assertEquals("1000 reels today",next.extras.getString(Notification.EXTRA_TITLE));assertEquals(ScrollTier.of(1000).argb,next.color)
            assertTrue(next.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("YouTube"))
            next.deleteIntent.send()
            Thread.sleep(1500);manager.cancel(LiveCounterNotification.ID)
            Thread.sleep(1000)
            publisher.update(SourceApp.YOUTUBE,1001,true);Thread.sleep(200)
            assertFalse("Dismissed session must stay hidden",manager.activeNotifications.any {it.id==LiveCounterNotification.ID})
            publisher.update(null,1000,true);Thread.sleep(200);assertFalse(manager.activeNotifications.any {it.id==LiveCounterNotification.ID})
            publisher.update(SourceApp.INSTAGRAM,1002,true);Thread.sleep(200)
            published("1002 reels today")
        } finally {
            publisher.clear()
            if(Build.VERSION.SDK_INT>=33 && old) shell("pm grant ${context.packageName} android.permission.POST_NOTIFICATIONS")
        }
    }
}
