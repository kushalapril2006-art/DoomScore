package com.gridcc.doomscore.android

import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.gridcc.doomscore.android.network.*
import com.gridcc.doomscore.android.ui.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Exercises real button/coroutine behavior; the gateway has no network implementation. */
class LeagueRefreshTest {
    @Test fun tapForcesFreshDataAndStopsRefreshingAfterSuccessOrFailure() {
        check(android.os.Build.MODEL.startsWith("sdk_gphone")) {"Synthetic emulator only"}
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val app=instrumentation.targetContext.applicationContext as DoomApplication
        check(!app.league.configured && !app.firebase.configured) {"Offline build required"}
        val fake=object:LeagueGateway {
            override val configured=true
            override val state=MutableStateFlow(LeagueState())
            val calls=AtomicInteger()
            var gate=CompletableDeferred<Unit>()
            var fail=false
            override suspend fun refresh(force:Boolean) {
                check(force) {"A manual refresh must bypass the standings cache"}
                calls.incrementAndGet();state.value=state.value.copy(busy=true,error=null)
                gate.await()
                state.value=state.value.copy(busy=false,error=if(fail) "Offline fixture failure" else null)
            }
            override suspend fun bootstrap() {}
            override suspend fun save(username:String,instagram:String,emoji:String,visible:Boolean,captcha:String?) {}
            override fun syncAsync() {}
            override suspend fun profileAction(username:String,action:String,reason:String) {}
            override suspend fun delete() {}
        }
        val automation=instrumentation.uiAutomation
        fun find(node:AccessibilityNodeInfo?,label:String):AccessibilityNodeInfo? {
            if(node==null) return null
            if(node.text?.toString()==label || node.contentDescription?.toString()==label) return node
            for(i in 0 until node.childCount) find(node.getChild(i),label)?.let {return it}
            return null
        }
        fun waitFor(label:String):AccessibilityNodeInfo {
            val end=android.os.SystemClock.elapsedRealtime()+15_000
            while(android.os.SystemClock.elapsedRealtime()<end) {
                find(automation.rootInActiveWindow,label)?.let {return it};Thread.sleep(100)
            }
            error("Missing refresh UI: $label")
        }
        fun enabledNode(label:String,enabled:Boolean):AccessibilityNodeInfo {
            val end=android.os.SystemClock.elapsedRealtime()+6000
            while(android.os.SystemClock.elapsedRealtime()<end) {
                var node=find(automation.rootInActiveWindow,label)
                // Compose may expose the icon label as a non-clickable leaf. Check the actual button.
                while(node!=null && node.isEnabled && !node.isClickable) node=node.parent
                if(node!=null && node.isEnabled==enabled) return node
                Thread.sleep(100)
            }
            val node=waitFor(label)
            error("$label expected enabled=$enabled, native class=${node.className}, clickable=${node.isClickable}, children=${node.childCount}, enabled=${node.isEnabled}")
        }
        fun tap() {
            var node=enabledNode("Refresh leaderboard",true)
            while(!node.isClickable) node=checkNotNull(node.parent)
            assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        }
        ActivityScenario.launch(MainActivity::class.java).use {scenario->
            scenario.onActivity {activity->activity.setContent {
                DoomTheme {
                    var message by remember {mutableStateOf("Ready")}
                    Column {LeagueRefreshButton(fake) {ok->message=if(ok) "Standings refreshed" else "Refresh failed"};Text(message)}
                }
            }}
            try {
                tap();val busy=enabledNode("Refreshing leaderboard",false)
                assertFalse(busy.isEnabled)
                busy.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Thread.sleep(200);assertEquals(1,fake.calls.get())
                fake.gate.complete(Unit)
                waitFor("Standings refreshed");waitFor("Refresh leaderboard")
                fake.gate=CompletableDeferred();fake.fail=true
                tap();waitFor("Refreshing leaderboard");assertEquals(2,fake.calls.get())
                fake.gate.complete(Unit)
                waitFor("Refresh failed");assertTrue(enabledNode("Refresh leaderboard",true).isEnabled)
            } finally {fake.gate.complete(Unit)}
        }
    }
}
