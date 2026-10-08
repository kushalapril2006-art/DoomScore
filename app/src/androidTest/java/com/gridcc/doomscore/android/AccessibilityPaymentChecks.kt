package com.gridcc.doomscore.android

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.core.SourceApp
import com.gridcc.doomscore.android.data.Preferences
import com.gridcc.doomscore.android.tracking.ReelAccessibilityService
import org.junit.Assert.*
import org.junit.Test

class AccessibilityPaymentChecks {
    @Test fun oldFloatingDefaultsMigrateToOptInWithoutChangingCountingConsent() {
        check(Build.MODEL.startsWith("sdk_gphone"))
        val real=InstrumentationRegistry.getInstrumentation().targetContext
        val name="payment-migration-${System.nanoTime()}"
        val isolated=object:ContextWrapper(real) {override fun getSharedPreferences(ignored:String,mode:Int)=real.getSharedPreferences(name,mode)}
        try {
            real.getSharedPreferences(name,Context.MODE_PRIVATE).edit().putBoolean("bubble",true).putBoolean("island",true).putBoolean("disclosed",true).putBoolean("enabled",true).commit()
            val migrated=Preferences(isolated)
            assertFalse(migrated.bubble);assertFalse(migrated.island)
            assertTrue(migrated.disclosed);assertTrue(migrated.enabled)
            migrated.bubble=true
            assertTrue("An explicit opt-in must survive reopening",Preferences(isolated).bubble)
        } finally {real.deleteSharedPreferences(name)}
    }
    @Test fun subscriptionsAreScopedAndDisconnectRemovesTheActualSystemServiceWithoutLosingCounts() {
        check(Build.MODEL.startsWith("sdk_gphone"))
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val app=context.applicationContext as DoomApplication
        val flags=UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES or (if(Build.VERSION.SDK_INT>=31) UiAutomation.FLAG_DONT_USE_ACCESSIBILITY else 0)
        val automation=instrumentation.getUiAutomation(flags)
        val setting=Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        val ours=ComponentName(context,ReelAccessibilityService::class.java)
        val original=Settings.Secure.getString(context.contentResolver,setting).orEmpty()
        val others=original.split(':').filter {it.isNotBlank() && ComponentName.unflattenFromString(it)!=ours}.joinToString(":")
        fun enableForThisIsolatedCheck(value:String) {
            automation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
            try {Settings.Secure.putString(context.contentResolver,setting,value)} finally {automation.dropShellPermissionIdentity()}
        }
        fun await(message:String,predicate:()->Boolean) {
            val deadline=SystemClock.elapsedRealtime()+12000
            while(!predicate() && SystemClock.elapsedRealtime()<deadline) Thread.sleep(100)
            assertTrue(message,predicate())
        }
        val manager=context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        fun info()=manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).firstOrNull {it.resolveInfo.serviceInfo.packageName==context.packageName}
        app.store.preferences.apply {disclosed=true;enabled=true;tracked=setOf(SourceApp.INSTAGRAM);bubble=true}
        try {
            enableForThisIsolatedCheck(others);Thread.sleep(300)
            enableForThisIsolatedCheck((others.split(':').filter {it.isNotBlank()}+ours.flattenToString()).joinToString(":"))
            await("Service must really connect") {ReelAccessibilityService.state.value.connected && info()?.packageNames?.toSet()==setOf("com.instagram.android",context.packageName)}
            val capabilities=info()!!.capabilities
            assertEquals(0,capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES)
            assertEquals(0,capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_REQUEST_FILTER_KEY_EVENTS)
            if(Build.VERSION.SDK_INT>=30) assertEquals(0,capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT)
            context.startActivity(Intent().setClassName("com.instagram.android","com.gridcc.doomscore.fixture.FixtureActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("reel","A").putExtra("busy",true))
            await("Supported reel feed must still be detected") {ReelAccessibilityService.state.value.app==SourceApp.INSTAGRAM}
            val before=app.store.day().total
            instrumentation.runOnMainSync {assertTrue(ReelAccessibilityService.disconnectForPayments())}
            await("Disconnect must remove the service from Android, not just pause") {!ReelAccessibilityService.isEnabled(context) && info()==null && !ReelAccessibilityService.state.value.connected}
            assertEquals(before,app.store.day().total)
            assertNull(ReelAccessibilityService.state.value.app)
        } finally {
            app.store.preferences.bubble=false
            enableForThisIsolatedCheck(original)
        }
    }
}
