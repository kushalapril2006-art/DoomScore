package com.gridcc.doomscore.android

import android.app.Application
import com.gridcc.doomscore.android.data.DoomStore
import com.gridcc.doomscore.android.network.BattleClient
import com.gridcc.doomscore.android.network.LeagueClient

class DoomApplication : Application() {
    lateinit var store: DoomStore; private set
    lateinit var battles: BattleClient; private set
    lateinit var league: LeagueClient; private set
    override fun onCreate() {
        super.onCreate()
        // Remove the retired reminders, including any notification left by an earlier version.
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).apply {cancel(100);deleteNotificationChannel("cap");cancel(com.gridcc.doomscore.android.tracking.LiveCounterNotification.ID)}
        store = DoomStore(this); battles = BattleClient(this, store); league = LeagueClient(this, store, battles)
    }
}
