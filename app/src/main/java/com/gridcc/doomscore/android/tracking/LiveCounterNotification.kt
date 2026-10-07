package com.gridcc.doomscore.android.tracking

import android.Manifest
import androidx.core.app.NotificationCompat
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.gridcc.doomscore.android.MainActivity
import com.gridcc.doomscore.android.R
import com.gridcc.doomscore.android.core.ScrollTier
import com.gridcc.doomscore.android.core.SourceApp

/** Best-effort native Live Update during an active feed; the OS controls promotion and layout. */
class LiveCounterNotification(private val context:Context) {
    companion object {const val CHANNEL="live_counter";const val ID=104;@Volatile internal var dismissed=false}
    private val manager=context.getSystemService(NotificationManager::class.java)
    private var last:Pair<SourceApp,Int>?=null
    private var iconLevel=-1
    private var icon:android.graphics.Bitmap?=null
    fun update(source:SourceApp?,count:Int,enabled:Boolean) {
        // Presentation failures are isolated from counting, including revoked permission.
        runCatching {
            if(source==null || !enabled || !manager.areNotificationsEnabled() ||
                (Build.VERSION.SDK_INT>=33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)) {clear();return}
            // Respect dismissal until this feed session ends, even when the score changes.
            if(dismissed) return
            val channel=manager.getNotificationChannel(CHANNEL)
            if(channel==null) manager.createNotificationChannel(NotificationChannel(CHANNEL,"Goob live counter",NotificationManager.IMPORTANCE_LOW).apply {
                description="Your reel score during an active feed; native Live Updates on supported phones"
                setSound(null,null);enableVibration(false);setShowBadge(false);lockscreenVisibility=Notification.VISIBILITY_PRIVATE
            })
            if(manager.getNotificationChannel(CHANNEL)?.importance==NotificationManager.IMPORTANCE_NONE) {clear();return}
            val frame=source to count
            if(last==frame) return
            val tier=ScrollTier.of(count)
            if(iconLevel!=tier.level) {icon=GoobIcon.bitmap(tier);iconLevel=tier.level}
            val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val dismiss=PendingIntent.getBroadcast(context,ID,Intent(context,LiveCounterDismissReceiver::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val public=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_doom).setContentTitle("Doomscore counter active").setContentText("Open Doomscore to see your score").build()
            var notification=NotificationCompat.Builder(context,CHANNEL).setSmallIcon(R.drawable.ic_doom).setLargeIcon(icon)
                .setContentTitle("$count reels today").setContentText("Goob · ${tier.title} · ${source.label}")
                .setColor(tier.argb).setContentIntent(open).setDeleteIntent(dismiss).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false)
                .setSilent(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public).build()
            if(Build.VERSION.SDK_INT>=36) {
                // Standard-style counter, never impersonating a call, media playback or delivery.
                notification=runCatching {
                    Notification.Builder.recoverBuilder(context,notification)
                        .setRequestPromotedOngoing(true).setShortCriticalText(count.toString())
                        .setSmallIcon(android.graphics.drawable.Icon.createWithBitmap(requireNotNull(icon))).build()
                }.getOrDefault(notification)
            }
            manager.notify(ID,notification);last=frame
        }
    }
    fun clear(force:Boolean=false) {
        if(force || last!=null) runCatching {manager.cancel(ID)}
        last=null
        dismissed=false
    }
}
