package com.gridcc.doomscore.android.tracking

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.os.Build
import com.gridcc.doomscore.android.DoomApplication

class CounterTile : TileService() {
    override fun onStartListening() { super.onStartListening(); update() }
    override fun onClick() {
        val prefs = (application as DoomApplication).store.preferences
        if (!prefs.disclosed || !ReelAccessibilityService.isEnabled(this)) return
        prefs.enabled = !prefs.enabled
        update()
    }
    private fun update() {
        val prefs = (application as DoomApplication).store.preferences
        val enabled = prefs.disclosed && prefs.enabled && ReelAccessibilityService.isEnabled(this)
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            if (Build.VERSION.SDK_INT >= 29) subtitle = if (enabled) "Counting" else "Paused"
            updateTile()
        }
    }
}
