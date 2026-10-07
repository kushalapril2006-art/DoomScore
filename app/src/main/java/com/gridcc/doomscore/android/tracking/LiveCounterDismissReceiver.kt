package com.gridcc.doomscore.android.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Explicit immutable notification intent; not callable by other apps. */
class LiveCounterDismissReceiver:BroadcastReceiver() {
    override fun onReceive(context:Context,intent:Intent) {LiveCounterNotification.dismissed=true}
}
