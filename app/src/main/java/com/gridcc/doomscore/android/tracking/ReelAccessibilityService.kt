package com.gridcc.doomscore.android.tracking

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.gridcc.doomscore.android.DoomApplication
import com.gridcc.doomscore.android.MainActivity
import com.gridcc.doomscore.android.core.*
import com.gridcc.doomscore.android.widget.CounterWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.*
import java.lang.ref.WeakReference

import java.util.concurrent.Executors

data class TrackingState(val connected:Boolean=false,val app:SourceApp?=null,val status:String="Counter not connected",val sessionCount:Int=0,val floatingVisible:Boolean=false,val presentation:String="")

class ReelAccessibilityService:AccessibilityService() {
    companion object {
        val state=MutableStateFlow(TrackingState())
        val lastFeedStatus=MutableStateFlow("Open Reels or Shorts to check counting")
        val lastFeedDetails=MutableStateFlow("No counting check yet")
        @Volatile private var connectedService:WeakReference<ReelAccessibilityService>?=null
        /** Resume a connected poller; never enable Android Accessibility on the user's behalf. */
        fun ensurePolling():Boolean {
            check(Looper.myLooper()==Looper.getMainLooper())
            val service=connectedService?.get() ?: return false
            if(!service.connection.connected) return false
            service.schedulePoll(0);return true
        }
        fun disconnectForPayments():Boolean {
            check(Looper.myLooper()==Looper.getMainLooper())
            val service=connectedService?.get() ?: return false
            service.deactivate("Disconnected for payments")
            service.disableSelf();return true
        }
        fun isEnabled(context:Context):Boolean {
            val wanted=ComponentName(context,ReelAccessibilityService::class.java)
            return Settings.Secure.getString(context.contentResolver,Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any {ComponentName.unflattenFromString(it)==wanted}==true
        }
    }
    private val handler=Handler(Looper.getMainLooper())
    private val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val counterDispatcher=Executors.newSingleThreadExecutor {r->Thread(r,"DoomScore counter").apply {isDaemon=true}}.asCoroutineDispatcher()
    private val counterScope=CoroutineScope(SupervisorJob()+counterDispatcher)
    private val connection=CounterConnection()
    private var preferenceJob:Job?=null
    private val app get()=application as DoomApplication
    private val store get()=app.store
    // Engine, metadata and database work are confined to the single counter worker.
    private lateinit var engine:ReelCounterEngine
    private lateinit var detector:FeedDetector
    private val feedPresentation=FeedPresentation()
    @Volatile private var foreground:SourceApp?=null
    private var sessionCount=0
    private var lastUi=0L
    private var lastWidget=0L
    @Volatile private var bubble:CounterPill?=null
    private var bubbleIsland=false
    private val liveCounter by lazy {LiveCounterNotification(this)}
    private var windowParams:WindowManager.LayoutParams?=null
    private var pollScheduled=false
    private var pollDueAt=0L
    private var inFlightToken:Long?=null
    private val windowManager get()=getSystemService(WINDOW_SERVICE) as WindowManager
    private val poll=object:Runnable {
        override fun run() {
            pollScheduled=false
            if(!eligible()) return
            if(inFlightToken!=null) {schedulePoll(500);return}
            val token=connection.token();inFlightToken=token
            counterScope.launch {
                try {if(valid(token)) scan(token)}
                catch(_:Exception) {recover(token,"Layout read interrupted · retrying")}
                finally {
                    handler.post {
                        if(inFlightToken==token) inFlightToken=null
                        if(valid(token) && eligible()) schedulePoll(if(foreground!=null) 250 else 1500)
                    }
                }
            }
        }
    }
    private fun valid(token:Long)=connection.accepts(token) && connectedService?.get()===this
    private fun eligible()=connection.connected && store.preferences.disclosed && store.preferences.enabled
    private fun schedulePoll(delay:Long=100) {
        if(!eligible()) return
        val due=SystemClock.elapsedRealtime()+delay
        if(!pollScheduled || due<pollDueAt) {
            handler.removeCallbacks(poll);pollScheduled=true;pollDueAt=due;handler.postDelayed(poll,delay)
        }
    }
    override fun onServiceConnected() {
        super.onServiceConnected()
        val token=connection.bind();inFlightToken=null
        connectedService=WeakReference(this)
        counterScope.launch {
            if(valid(token)) {
                runCatching {
                    detector=FeedDetector(store.preferences.salt);engine=ReelCounterEngine(store)
                    foreground=null;sessionCount=0;feedPresentation.reset()
                }.onFailure {present(token) {state.value=TrackingState(true,status="Counter couldn't initialize · reopen Accessibility");lastFeedStatus.value=state.value.status}}
            }
        }
        updateSubscriptions()
        state.value=TrackingState(true,status=if(store.preferences.disclosed) "Ready when you scroll" else "Open DoomScore to finish setup")
        lastFeedStatus.value="Counter connected · open Reels or Shorts"
        preferenceJob?.cancel()
        preferenceJob=serviceScope.launch {
            store.preferences.revisions.collect {
                updateSubscriptions()
                val prefs=store.preferences
                if(!prefs.disclosed || !prefs.enabled) {hideBubble();liveCounter.clear(force=true)}
                if(!prefs.disclosed || !prefs.enabled || foreground?.let {it !in prefs.tracked}==true) leave(if(prefs.enabled) "Ready when you scroll" else "Counter paused")
                if(!prefs.bubble) hideBubble()
                if(!prefs.liveNotification) liveCounter.clear(force=true)
                if(eligible()) schedulePoll()
            }
        }
        schedulePoll()
    }
    private fun updateSubscriptions() {
        // Keep the package filter nonempty, including when all counters are paused.
        // An OEM refusing a runtime update must not crash the binding; the XML filter stays narrow.
        runCatching {
            val prefs=store.preferences
            val packages=if(prefs.disclosed && prefs.enabled) prefs.tracked.flatMap {it.packages} else emptyList()
            serviceInfo=serviceInfo.apply {
                packageNames=(packages+packageName).distinct().toTypedArray()
                flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
        }.onFailure {lastFeedStatus.value="Android couldn't update the counter subscription · retry in Accessibility"}
    }
    override fun onAccessibilityEvent(event:AccessibilityEvent?) {
        if(!connection.connected || event==null) return
        if(!eligible()) {leave("Counter paused");return}
        if(event.eventType==AccessibilityEvent.TYPE_WINDOWS_CHANGED) {schedulePoll();return}
        val eventPackage=event.packageName?.toString() ?: return
        val source=SourceApp.fromPackage(eventPackage)
        if(source!=null && source !in store.preferences.tracked) {leave();return}
        if(source==null) {
            if(event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && eventPackage !in setOf("android","com.android.systemui",packageName)) leave()
            if(eventPackage==packageName && event.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className?.toString()==MainActivity::class.java.name) leave()
            return
        }
        schedulePoll()
    }
    private fun present(token:Long,block:()->Unit) {
        handler.post {if(valid(token)) block()}
    }
    private fun recover(token:Long,status:String="Waiting for a readable reel") {
        if(!valid(token)) return
        if(::engine.isInitialized) engine.stop()
        feedPresentation.reset()
        val session=sessionCount
        present(token) {
            hideBubble();liveCounter.clear()
            state.value=TrackingState(true,status=status,sessionCount=session)
            lastFeedStatus.value="${foreground?.label ?: "Feed"}: $status"
        }
    }
    private fun scan(token:Long) {
        if(!valid(token) || !eligible() || !::engine.isInitialized) return
        val prefs=store.preferences
        val wall=System.currentTimeMillis()
        val power=getSystemService(POWER_SERVICE) as android.os.PowerManager
        val keyguard=getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if(!power.isInteractive || keyguard.isKeyguardLocked) {if(foreground!=null) leaveWorker(token);return}
        val root=try {readableRoot()} catch(_:Exception) {null}
        if(root==null) {recover(token);return}
        try {
            val source=SourceApp.fromPackage(root.packageName?.toString())
            if(source==null || source !in prefs.tracked) {if(foreground!=null) leaveWorker(token);return}
            if(source!=foreground) {engine.stop();feedPresentation.reset();foreground=source;sessionCount=0}
            val viewport=Rect();root.getBoundsInScreen(viewport)
            if(viewport.isEmpty) {recover(token);return}
            val snapshot=snapshot(root)
            // A disconnect/pause during provider IPC cannot turn a queued observation into a count.
            if(!valid(token) || !eligible() || source !in prefs.tracked) return
            val feed=detector.inspect(source,snapshot.nodes,viewport.bottom,viewport.top,viewport.left,viewport.right)
            val observation=feed.observation
            val before=store.day().total
            val now=SystemClock.elapsedRealtime()
            if(!valid(token) || !eligible()) return
            engine.observe(observation,now,wall)
            val presentationSource=feedPresentation.active(source,feed.feedVisible,now)
            val today=store.day();sessionCount+=(today.total-before).coerceAtLeast(0)
            val session=sessionCount
            val status=if(observation!=null) engine.status else if(feed.feedVisible) "Feed detected · waiting for readable metadata" else "No readable reel metadata · open Reels or Shorts"
            val details="${com.gridcc.doomscore.android.BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.SDK_INT} · ${source.label} · nodes ${snapshot.nodes.size} · scan limited ${snapshot.truncated} · captions ${feed.captionFields} · creators ${feed.creatorFields} · labels ${feed.labelFields} · panels ${feed.panelFields} · buttons ${feed.buttonFields} · today ${today.apps[source]?.count ?: 0}"
            present(token) {
                if(!eligible() || source !in prefs.tracked) {hideBubble();liveCounter.clear(force=true);return@present}
                val display=runCatching {if(presentationSource!=null && prefs.bubble) showBubble(today.total) else hideBubble()}
                state.value=TrackingState(true,if(feed.feedVisible) source else null,status,session,bubble!=null,
                    if(display.isFailure || (presentationSource!=null && prefs.bubble && bubble==null)) "Phone couldn't display Goob · check app settings" else "")
                lastFeedStatus.value="${source.label}: $status";lastFeedDetails.value=details
                liveCounter.update(presentationSource,today.total,prefs.liveNotification)
            }
            if(wall-lastWidget>2000 && today.total!=before) {lastWidget=wall;runCatching {CounterWidget.updateAll(this)}}
            if(wall-lastUi>60000) {lastUi=wall;runCatching {app.battles.syncAsync();app.league.syncAsync()}}
        } finally {@Suppress("DEPRECATION") root.recycle()}
    }
    @Suppress("DEPRECATION")
    private fun readableRoot():AccessibilityNodeInfo? {
        val active=rootInActiveWindow
        if(active!=null && !(active.packageName?.toString()==packageName && bubble!=null)) return active
        // Some providers make an accessibility overlay the active window even after touch ends.
        // Resolve only a uniquely input-focused application window; never a cached/PiP background.
        // scan() checks its root package before reading any hierarchy, including for payment apps.
        val available=try {windows} catch(_:Exception) {active?.recycle();return null}
        try {
            val index=FocusedFeedWindow.select(available.map {FeedWindow(it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION,it.isFocused)})
            return index?.let {available[it].root}
        } finally {active?.recycle();available.forEach {it.recycle()}}
    }
    private fun snapshot(root: AccessibilityNodeInfo):UiSnapshotReader.Result = UiSnapshotReader(object:UiSnapshotReader.Access<AccessibilityNodeInfo> {
        override fun describe(node:AccessibilityNodeInfo,parent:Int):UiNode {
            val bounds=Rect();node.getBoundsInScreen(bounds)
            return UiNode(node.viewIdResourceName.orEmpty().take(256),node.text?.toString().orEmpty().take(4096),node.contentDescription?.toString().orEmpty().take(4096),
                node.isVisibleToUser,bounds.top,bounds.bottom,bounds.left,bounds.right,parent,node.className?.toString().orEmpty(),node.isEditable)
        }
        override fun visible(node:AccessibilityNodeInfo)=node.isVisibleToUser
        override fun childCount(node:AccessibilityNodeInfo)=node.childCount
        override fun child(node:AccessibilityNodeInfo,index:Int)=node.getChild(index)
        @Suppress("DEPRECATION") override fun release(node:AccessibilityNodeInfo) {node.recycle()}
    }).read(root)
    private fun leave(status:String="Ready when you scroll") {
        val token=connection.token()
        counterScope.launch {if(valid(token)) leaveWorker(token,status)}
    }
    private fun leaveWorker(token:Long,status:String="Ready when you scroll") {
        if(::engine.isInitialized) engine.stop()
        feedPresentation.reset();foreground=null;sessionCount=0
        present(token) {hideBubble();liveCounter.clear(force=true);state.value=TrackingState(true,status=status)}
        runCatching {CounterWidget.updateAll(this)}
    }
    private fun showBubble(count: Int) {
        val island=store.preferences.island
        if(bubble!=null && bubbleIsland!=island) hideBubble()
        var view = bubble
        if (view == null) {
            val density = resources.displayMetrics.density
            val safeTop=runCatching {
                if(android.os.Build.VERSION.SDK_INT>=30) windowManager.currentWindowMetrics.windowInsets
                    .getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout()).top
                else 0
            }.getOrDefault(0).coerceAtLeast(runCatching {
                val id=resources.getIdentifier("status_bar_height","dimen","android")
                if(id!=0) resources.getDimensionPixelSize(id) else (24*density).toInt()
            }.getOrDefault((24*density).toInt()))
            view = CounterPill(this)
            val params = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT).apply {
                // Keep the status bar/camera usable: the island lives below system insets.
                gravity = Gravity.TOP or if(island) Gravity.CENTER_HORIZONTAL else Gravity.END
                x = if(island) 0 else (12*density).toInt()
                y = safeTop + ((if(island) 8 else 110)*density).toInt()
            }
            view.setOnApplyWindowInsetsListener { target,insets ->
                val top=if(android.os.Build.VERSION.SDK_INT>=30) insets.getInsetsIgnoringVisibility(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.displayCutout()).top else @Suppress("DEPRECATION") insets.systemWindowInsetTop
                val next=params.y.coerceAtLeast(top.coerceAtLeast(safeTop)+(8*density).toInt())
                if(next!=params.y) {params.y=next;target.post {runCatching {windowManager.updateViewLayout(target,params)}}}
                insets
            }
            var downX = 0f; var downY = 0f; var originalX = 0; var originalY = 0; var moved = false
            view.setOnClickListener { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            view.setOnTouchListener { target, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; originalX = params.x; originalY = params.y; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - downX; val dy = event.rawY - downY
                        moved = moved || kotlin.math.abs(dx) + kotlin.math.abs(dy) > 10*density
                        val half=(resources.displayMetrics.widthPixels-target.width).coerceAtLeast(0)/2
                        params.x = if(island) (originalX+dx).toInt().coerceIn(-half,half) else (originalX-dx).toInt().coerceIn(0,half*2)
                        val bottom=(resources.displayMetrics.heightPixels-target.height-(80*density).toInt()).coerceAtLeast(safeTop)
                        params.y = (originalY+dy).toInt().coerceIn(safeTop,bottom)
                        runCatching { windowManager.updateViewLayout(target, params) }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) target.performClick()
                    MotionEvent.ACTION_CANCEL -> moved=true
                }
                true
            }
            view.update(count)
            if (runCatching { windowManager.addView(view, params) }.isSuccess) { bubble = view; windowParams = params; bubbleIsland=island }
        }
        view.update(count)
    }
    private fun hideBubble() { bubble?.let { runCatching { windowManager.removeViewImmediate(it) } }; bubble = null; windowParams = null }
    private fun deactivate(status:String="Counter disconnected by Android · reopen Accessibility") {
        connection.release();preferenceJob?.cancel()
        handler.removeCallbacks(poll);pollScheduled=false;inFlightToken=null
        hideBubble()
        // Teardown from an older instance cannot clear a newer service's state/notification.
        if(connectedService?.get()===this) {
            liveCounter.clear(force=true);state.value=TrackingState(status=status);lastFeedStatus.value=status
            connectedService=null
        }
        counterScope.launch {if(!connection.connected) {if(::engine.isInitialized) engine.stop();feedPresentation.reset();foreground=null}}
    }
    override fun onInterrupt() {leave("Counter interrupted · retrying");schedulePoll()}
    override fun onUnbind(intent:Intent?):Boolean {deactivate();return super.onUnbind(intent)}
    override fun onDestroy() {
        deactivate();serviceScope.cancel();counterScope.cancel();counterDispatcher.close()
        super.onDestroy()
    }
}
