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

data class TrackingState(val connected: Boolean = false, val app: SourceApp? = null, val status: String = "Counter not connected", val sessionCount: Int = 0)

class ReelAccessibilityService : AccessibilityService() {
    companion object {
        val state = MutableStateFlow(TrackingState())
        private var connectedService:WeakReference<ReelAccessibilityService>?=null
        /** Explicit user action: fully disable the service through Android, never silently re-enable it. */
        fun disconnectForPayments():Boolean {
            val service=connectedService?.get() ?: return false
            check(Looper.myLooper()==Looper.getMainLooper())
            service.leave()
            service.disableSelf()
            return true
        }
        fun isEnabled(context: Context): Boolean {
            val wanted = ComponentName(context, ReelAccessibilityService::class.java)
            return Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?.split(':')?.any { ComponentName.unflattenFromString(it) == wanted } == true
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private var preferenceJob:Job?=null
    private val app get() = application as DoomApplication
    private val store get() = app.store
    private lateinit var engine: ReelCounterEngine
    private lateinit var detector: FeedDetector
    private var foreground: SourceApp? = null
    private var sessionCount = 0
    private var lastUi = 0L
    private var lastWidget = 0L
    private var bubble: CounterPill? = null
    private var bubbleIsland = false
    private val liveCounter by lazy { LiveCounterNotification(this) }
    private var windowParams: WindowManager.LayoutParams? = null
    private var pollScheduled = false
    private val windowManager get() = getSystemService(WINDOW_SERVICE) as WindowManager
    private val poll = object : Runnable {
        override fun run() {
            pollScheduled = false
            scan()
            if (foreground != null) { pollScheduled = true; handler.postDelayed(this, 200) }
        }
    }
    override fun onServiceConnected() {
        detector = FeedDetector(store.preferences.salt)
        engine = ReelCounterEngine(store)
        connectedService=WeakReference(this)
        updateSubscriptions()
        state.value = TrackingState(connected = true, status = if (store.preferences.disclosed) "Ready when you scroll" else "Open Doomscore to finish setup")
        preferenceJob?.cancel()
        preferenceJob=serviceScope.launch {
            store.preferences.revisions.collect {
                updateSubscriptions()
                val prefs=store.preferences
                if(!prefs.disclosed || !prefs.enabled || foreground?.let {it !in prefs.tracked}==true) leave(if(prefs.enabled) "Ready when you scroll" else "Counter paused")
                if(!prefs.bubble) hideBubble()
                if(!prefs.liveNotification) liveCounter.clear(force=true)
            }
        }
    }
    private fun updateSubscriptions() {
        val prefs=store.preferences
        val packages=if(prefs.disclosed && prefs.enabled) prefs.tracked.flatMap {it.packages} else emptyList()
        // Keep this non-empty: null or an empty package filter subscribes to all apps.
        serviceInfo=serviceInfo.apply {packageNames=(packages+packageName).distinct().toTypedArray()}
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!::engine.isInitialized || event == null) return
        // Even event metadata is ignored until consent, and while the user pauses.
        if (!store.preferences.disclosed || !store.preferences.enabled) {
            if(foreground != null) leave()
            return
        }
        val packageName = event.packageName?.toString() ?: return
        // Never read the hierarchy of an unrelated application.
        val source = SourceApp.fromPackage(packageName)
        if(source != null && source !in store.preferences.tracked) {
            if(foreground != null) leave()
            return
        }
        if (source == null) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && packageName !in setOf("android", "com.android.systemui", this.packageName)) leave()
            // Our non-focusable count bubble must not be mistaken for opening the dashboard.
            if (packageName == this.packageName && event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.className?.toString() == MainActivity::class.java.name) leave()
            return
        }
        if (source != foreground) { leave(); foreground = source; sessionCount = 0 }
        // Frequent video UI events must not keep postponing the scan indefinitely.
        if (!pollScheduled) { pollScheduled = true; handler.postDelayed(poll, 100) }
    }
    private fun scan() {
        val source = foreground ?: return
        val prefs = store.preferences
        val wall = System.currentTimeMillis()
        if (!prefs.disclosed || !prefs.enabled || source !in prefs.tracked) {
            leave("Counter paused"); return
        }
        val power = getSystemService(POWER_SERVICE) as android.os.PowerManager
        val keyguard = getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager
        if (!power.isInteractive || keyguard.isKeyguardLocked) { leave(); return }
        val root = try { rootInActiveWindow } catch (_: Exception) { null }
        if (root == null) { leave(); return }
        try {
            // Accessibility's active window follows a finger touching our non-focusable pill.
            // Keep the current feed/session while moving it; MainActivity events still call leave().
            if(root.packageName?.toString()==packageName && bubble!=null) return
            if (SourceApp.fromPackage(root.packageName?.toString()) != source) { leave(); return }
            val before = store.day().total
            val snapshot = snapshot(root)
            val observation = detector.detect(source, snapshot, resources.displayMetrics.heightPixels)
            engine.observe(observation, SystemClock.elapsedRealtime(), wall)
            val today = store.day()
            sessionCount += (today.total - before).coerceAtLeast(0)
            state.value = TrackingState(true, if (observation != null) source else null, if (observation != null) engine.status else "No reel feed detected", sessionCount)
            runCatching { if (observation != null && prefs.bubble) showBubble(today.total) else hideBubble() }
            liveCounter.update(if(observation!=null) source else null,today.total,prefs.liveNotification)
            if (wall - lastWidget > 2_000 && today.total != before) { lastWidget = wall; CounterWidget.updateAll(this) }
            if (wall - lastUi > 60_000) { lastUi = wall; app.battles.syncAsync(); app.league.syncAsync() }
        } catch (_: Exception) {
            // A disappearing/changed app hierarchy must not crash the counter service.
            engine.stop(); hideBubble(); liveCounter.clear()
            state.value = TrackingState(true, status = "Waiting for a readable reel")
        } finally {
            @Suppress("DEPRECATION") root.recycle()
        }
    }
    private fun snapshot(root: AccessibilityNodeInfo): List<UiNode> {
        val result = mutableListOf<UiNode>()
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.add(root to -1)
        while (queue.isNotEmpty() && result.size < 800) {
            val (node, parent) = queue.removeFirst()
            val bounds = Rect(); node.getBoundsInScreen(bounds)
            val index = result.size
            result += UiNode(node.viewIdResourceName.orEmpty().take(256), node.text?.toString().orEmpty().take(4096), node.contentDescription?.toString().orEmpty().take(4096),
                node.isVisibleToUser, bounds.top, bounds.bottom, bounds.left, bounds.right, parent, node.className?.toString().orEmpty(), node.isEditable)
            repeat(node.childCount.coerceIn(0, (800-result.size-queue.size).coerceAtLeast(0))) { child -> node.getChild(child)?.let { queue.add(it to index) } }
            if (node !== root) { @Suppress("DEPRECATION") node.recycle() }
        }
        while (queue.isNotEmpty()) { @Suppress("DEPRECATION") queue.removeFirst().first.recycle() }
        return result
    }
    private fun leave(status:String="Ready when you scroll") {
        handler.removeCallbacks(poll)
        pollScheduled = false
        if (::engine.isInitialized) engine.stop()
        foreground = null; hideBubble(); liveCounter.clear(force=true)
        state.value = TrackingState(true, status = status)
        CounterWidget.updateAll(this)
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
    override fun onInterrupt() { leave() }
    override fun onUnbind(intent:Intent?):Boolean {
        preferenceJob?.cancel();leave();state.value=TrackingState()
        if(connectedService?.get()===this) connectedService=null
        return super.onUnbind(intent)
    }
    override fun onDestroy() {
        preferenceJob?.cancel();serviceScope.cancel();leave();state.value=TrackingState()
        if(connectedService?.get()===this) connectedService=null
        super.onDestroy()
    }
}
