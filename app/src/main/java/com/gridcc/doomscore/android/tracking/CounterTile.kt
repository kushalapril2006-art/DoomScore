package com.gridcc.doomscore.android.tracking

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.os.Build
import android.app.PendingIntent
import android.content.Intent
import com.gridcc.doomscore.android.MainActivity
import com.gridcc.doomscore.android.DoomApplication

class CounterTile : TileService() {
    override fun onStartListening() { super.onStartListening(); update() }
    override fun onClick() {
        val prefs = (application as DoomApplication).store.preferences
        if (!prefs.disclosed || !ReelAccessibilityService.isEnabled(this)) {
            val open=Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if(Build.VERSION.SDK_INT>=34) startActivityAndCollapse(PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            else openOnOlderAndroid(open)
            return
        }
        prefs.enabled = !prefs.enabled
        update()
    }
    // The PendingIntent overload was added in API 34; older Android requires the Intent overload.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openOnOlderAndroid(open:Intent) {
        check(Build.VERSION.SDK_INT<34)
        startActivityAndCollapse(open)
    }
    private fun update() {
        val prefs = (application as DoomApplication).store.preferences
        val connected=ReelAccessibilityService.isEnabled(this)
        val enabled = prefs.disclosed && prefs.enabled && connected
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = if(!connected) "Disconnected" else if (enabled) "Counting" else "Paused · access on"
            updateTile()
        }
    }
}
