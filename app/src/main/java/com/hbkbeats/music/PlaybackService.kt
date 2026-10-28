package com.hbkbeats.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

/** Keeps playback alive in the background and shows lock-screen / notification controls. */
class PlaybackService : Service() {
    companion object {
        const val ACTION_REFRESH = "refresh"
        const val ACTION_TOGGLE = "toggle"
        const val ACTION_NEXT = "next"
        const val ACTION_PREV = "prev"
        const val ACTION_STOP = "stop"
        private const val CHANNEL = "playback"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PrivateStore.init(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> PlayerManager.toggle()
            ACTION_NEXT -> PlayerManager.next()
            ACTION_PREV -> PlayerManager.prev()
            ACTION_STOP -> {
                PlayerManager.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(1, buildNotification())
        return START_STICKY
    }

    private fun act(icon: Int, title: String, action: String) = NotificationCompat.Action(
        icon, title,
        PendingIntent.getService(
            this, action.hashCode(),
            Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    )

    private fun buildNotification(): Notification {
        val t = PlayerManager.current
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val playIcon = if (PlayerManager.isPlaying) android.R.drawable.ic_media_pause
        else android.R.drawable.ic_media_play
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(if (t != null && PrivateStore.isPrivate(t)) "HbkBeats Music" else t?.title ?: "HbkBeats Music")
            .setContentText(if (t != null && PrivateStore.isPrivate(t)) "" else t?.artist ?: "")
            .setContentIntent(open)
            .setOngoing(PlayerManager.isPlaying)
            .setDeleteIntent(
                PendingIntent.getService(
                    this, 99, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(act(android.R.drawable.ic_media_previous, "Previous", ACTION_PREV))
            .addAction(act(playIcon, "Play/Pause", ACTION_TOGGLE))
            .addAction(act(android.R.drawable.ic_media_next, "Next", ACTION_NEXT))
            .setStyle(MediaStyle().setShowActionsInCompactView(0, 1, 2))
            .build()
    }
}
