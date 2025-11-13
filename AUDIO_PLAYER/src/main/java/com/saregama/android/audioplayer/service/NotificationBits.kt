package com.saregama.android.audioplayer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.media.session.MediaButtonReceiver
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.media3.session.legacy.MediaControllerCompat
import androidx.media3.session.legacy.MediaSessionCompat
import com.saregama.android.audioplayer.PlayerConfig

internal object CustomActions {
    const val TOGGLE_FAVORITE = "com.saregama.android.audioplayer.TOGGLE_FAVORITE"
    const val DOWNLOAD = "com.saregama.android.audioplayer.DOWNLOAD"
    val CMD_TOGGLE_FAVORITE = androidx.media3.session.SessionCommand(TOGGLE_FAVORITE, Bundle.EMPTY)
    val CMD_DOWNLOAD = androidx.media3.session.SessionCommand(DOWNLOAD, Bundle.EMPTY)
}

internal class NotificationActionReceiver(private val onAction: (String) -> Unit) : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) { intent?.action?.let(onAction) }
}

internal class NotificationProvider(
    private val context: Context,
    private val config: PlayerConfig,
) {
    private val nm = NotificationManagerCompat.from(context)
    private val channelId = config.notificationChannelId

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(NotificationChannel(channelId, config.notificationChannelName, NotificationManager.IMPORTANCE_LOW))
        }
    }

    fun build(session: MediaSession, isPlaying: Boolean): Notification {
        val player = session.player
        val contentPI: PendingIntent? = session.sessionActivity

        // Media3 style (no compat token needed)
        val style = MediaStyleNotificationHelper.MediaStyle(session)
            .setShowActionsInCompactView(0, 1, 2)

        // Transport actions via MediaButtonReceiver
        val prev = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Prev",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
            )
        )

        val playPause = if (isPlaying) {
            NotificationCompat.Action(
                android.R.drawable.ic_media_pause, "Pause",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context, PlaybackStateCompat.ACTION_PAUSE
                )
            )
        } else {
            NotificationCompat.Action(
                android.R.drawable.ic_media_play, "Play",
                MediaButtonReceiver.buildMediaButtonPendingIntent(
                    context, PlaybackStateCompat.ACTION_PLAY
                )
            )
        }

        val next = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next",
            MediaButtonReceiver.buildMediaButtonPendingIntent(
                context, PlaybackStateCompat.ACTION_SKIP_TO_NEXT
            )
        )

        // Your custom actions (Fav / Download) via broadcasts
        val favPI = PendingIntent.getBroadcast(
            context, 101, Intent(CustomActions.TOGGLE_FAVORITE),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val fav = NotificationCompat.Action(android.R.drawable.btn_star_big_on, "Fav", favPI)

        val dlPI = PendingIntent.getBroadcast(
            context, 102, Intent(CustomActions.DOWNLOAD),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val dl = NotificationCompat.Action(android.R.drawable.stat_sys_download, "DL", dlPI)

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(config.smallIconResId ?: android.R.drawable.stat_sys_headset)
            .setContentTitle(player.mediaMetadata.title)
            .setContentText(player.mediaMetadata.artist)
            .setContentIntent(contentPI)
            .setOnlyAlertOnce(true)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(style)
            .addAction(prev)
            .addAction(playPause)
            .addAction(next)
            .addAction(fav)
            .addAction(dl)
            .build()
    }

    fun notify(id: Int, notification: Notification) = nm.notify(id, notification)
}

// Extension to build transport control pending intents via compat controller
private fun MediaControllerCompat.TransportControls.buildTransportControlsPendingIntent(action: String): PendingIntent {
    val intent = Intent(action)
    return PendingIntent.getBroadcast(
        (this as Any) as Context, // not strictly correct; kept simple for demo
        action.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
    )
}
