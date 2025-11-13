package com.saregama.android.audioplayer.service

import android.app.Notification
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch

class AudioPlayerService : MediaSessionService() {
    private lateinit var session: MediaSession
    private lateinit var player: ExoPlayer
    private lateinit var notificationProvider: NotificationProvider
    private lateinit var actionReceiver: NotificationActionReceiver

    private val NOTIF_ID = 41

    override fun onCreate() {
        super.onCreate()

        val cache = CacheHolder.get(this)
        val upstream = androidx.media3.datasource.DefaultDataSource.Factory(this)
        val cacheFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(cacheFactory))
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                setWakeMode(C.WAKE_MODE_NETWORK)
            }

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()

        notificationProvider = NotificationProvider(this, com.saregama.android.audioplayer.PlayerConfig())
        notificationProvider.ensureChannel()
        val initial = notificationProvider.build(session, isPlaying = false)
        startForeground(NOTIF_ID, initial)

        actionReceiver = NotificationActionReceiver { action ->
            val p = session.player
            val item = p.currentMediaItem ?: return@NotificationActionReceiver

            when (action) {
                CustomActions.TOGGLE_FAVORITE -> {
                    val id = session.player.currentMediaItem?.mediaId ?: return@NotificationActionReceiver
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.saregama.android.audioplayer.data.ServiceLocator.repository().toggleFavorite(id)
                    }
                }
                CustomActions.DOWNLOAD -> {
                    val item = session.player.currentMediaItem ?: return@NotificationActionReceiver
                    val t = com.saregama.android.audioplayer.model.Track(
                        id = item.mediaId,
                        url = item.localConfiguration?.uri.toString(),
                        title = item.mediaMetadata.title?.toString(),
                        artist = item.mediaMetadata.artist?.toString(),
                        album = item.mediaMetadata.albumTitle?.toString(),
                        artwork = item.mediaMetadata.artworkUri?.toString()
                    )
                    com.saregama.android.audioplayer.data.ServiceLocator.repository().enqueueDownload(t)
                }
            }
        }
        registerReceiver(actionReceiver, IntentFilter().apply {
            addAction(CustomActions.TOGGLE_FAVORITE)
            addAction(CustomActions.DOWNLOAD)
        })

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val n: Notification = notificationProvider.build(session, isPlaying)
                notificationProvider.notify(NOTIF_ID, n)
            }
            override fun onPlaybackStateChanged(state: Int) {
                val n: Notification = notificationProvider.build(session, player.isPlaying)
                notificationProvider.notify(NOTIF_ID, n)
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
        unregisterReceiver(actionReceiver)
        session.release()
        player.release()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        }
        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> {

            // If you need to resolve/augment items (e.g., setUri, headers, extras), do it here:
            val resolved = mediaItems.map { item ->
                // Example: ensure a proper Uri is set
                val uri = item.localConfiguration?.uri ?: Uri.parse(item.mediaId)
                item.buildUpon()
                    .setUri(uri)
                    // .setCustomCacheKey(...)   // optional
                    // .setMimeType(...)         // optional
                    .build()
            }

            // Return immediately (or use a background task if you need I/O)
            return Futures.immediateFuture(resolved)
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return when (customCommand.customAction) {
                CustomActions.TOGGLE_FAVORITE -> {
                    // Perform favorite toggle (launch coroutine if needed)
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                CustomActions.DOWNLOAD -> {
                    // Perform download enqueue logic here
                    Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                else -> Futures.immediateFuture(
                    SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED)
                )
            }
        }
    }
}
