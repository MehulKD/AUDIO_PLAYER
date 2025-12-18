package com.saregama.android.audioplayer.service

import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import androidx.media3.session.CommandButton.ICON_HEART_FILLED
import androidx.media3.session.CommandButton.ICON_HEART_UNFILLED
import androidx.media3.session.CommandButton.ICON_SHARE
import androidx.media3.session.CommandButton.ICON_THUMB_DOWN_FILLED
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.saregama.android.audioplayer.R
import com.saregama.android.audioplayer.service.CustomActions.CMD_DOWNLOAD
import com.saregama.android.audioplayer.service.CustomActions.CMD_TOGGLE_FAVORITE
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AudioPlayerService : MediaSessionService() {
    private lateinit var session: MediaSession
    private lateinit var player: ExoPlayer
//    private lateinit var notificationProvider: NotificationProvider
//    private lateinit var actionReceiver: NotificationActionReceiver

//    private val NOTIF_ID = 41

    private val serviceScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private var favoriteIds= setOf<String>()
    private var downloadIds= setOf<String>()

    @OptIn(UnstableApi::class)
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
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true
                )
                setWakeMode(C.WAKE_MODE_NETWORK)
            }

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()

//        notificationProvider = NotificationProvider(this, com.saregama.android.audioplayer.PlayerConfig())
//        notificationProvider.ensureChannel()
//        val initial = notificationProvider.build(session, isPlaying = false)
//        startForeground(NOTIF_ID, initial)

        /*actionReceiver = NotificationActionReceiver { action ->
            val player = session.player
            val item = player.currentMediaItem ?: return@NotificationActionReceiver

            when (action) {
                CustomActions.TOGGLE_FAVORITE -> {
                    serviceScope.launch {
                        ServiceLocator.repository().toggleFavorite(item.mediaId)
                    }
                }

                CustomActions.DOWNLOAD -> {
                    val t = Track(
                        id = item.mediaId,
                        url = item.localConfiguration?.uri.toString(),
                        title = item.mediaMetadata.title?.toString(),
                        artist = item.mediaMetadata.artist?.toString(),
                        album = item.mediaMetadata.albumTitle?.toString(),
                        artwork = item.mediaMetadata.artworkUri?.toString()
                    )
                    serviceScope.launch {
                        ServiceLocator.repository().enqueueDownload(t)
                    }
                }
            }
        }

        registerReceiver(actionReceiver, IntentFilter().apply {
            addAction(CustomActions.TOGGLE_FAVORITE)
            addAction(CustomActions.DOWNLOAD)
        }, RECEIVER_NOT_EXPORTED)*/
        // Keep favorites cache in sync with DB
        serviceScope.launch {
            com.saregama.android.audioplayer.data
                .ServiceLocator
                .repository()
                .observeFavorites()        // Flow<Set<String>>
                .collect { set ->
                    favoriteIds = set
                    // whenever favorites change, update buttons for current track
                    refreshCommandButtonsForCurrent()
                }
        }
        serviceScope.launch {
            com.saregama.android.audioplayer.data
                .ServiceLocator
                .repository()
                .observeDownloadIds()        // Flow<Set<String>>
                .collect { set ->
                    downloadIds = set
                    // whenever favorites change, update buttons for current track
                    refreshCommandButtonsForCurrent()
                }
        }

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshCommandButtonsForCurrent()
            }
            override fun onPlaybackStateChanged(state: Int) {
            }
        })
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onDestroy() {
//        unregisterReceiver(actionReceiver)
        serviceScope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    private inner class SessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val availableSessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
                .buildUpon()
                .add(CMD_TOGGLE_FAVORITE) // Add your custom command
                .add(CMD_DOWNLOAD) // Add your custom command
                .build()

            // Define which buttons should be shown and where
            val favButton = getFavCommandButton(false)
            val downloadButton = getDownloadCommandButton(false)
            val customLayout = listOf(favButton, downloadButton)
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(availableSessionCommands)
                .setCustomLayout(customLayout) // Set the custom buttons for the notification/controller
                .build()
//            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
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

        @OptIn(UnstableApi::class)
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {

            return when (customCommand.customAction) {
                CustomActions.TOGGLE_FAVORITE -> {
                    val mediaId = session.player.currentMediaItem?.mediaId
                    if (mediaId == null) {
                        Futures.immediateFuture(
                            SessionResult(SessionError.ERROR_BAD_VALUE)
                        )
                    } else {
                        serviceScope.launch {
                            com.saregama.android.audioplayer.data
                                .ServiceLocator
                                .repository()
                                .toggleFavorite(mediaId)
                        }
                        Futures.immediateFuture(
                            SessionResult(SessionResult.RESULT_SUCCESS)
                        )
                    }
                }

                CustomActions.DOWNLOAD -> {
                    val item = session.player.currentMediaItem
                    val uri = item?.localConfiguration?.uri

                    if (item == null || uri == null) {
                        Futures.immediateFuture(
                            SessionResult(SessionError.ERROR_BAD_VALUE)
                        )
                    } else {
                        val t = com.saregama.android.audioplayer.model.Track(
                            id = item.mediaId,
                            url = uri.toString(),
                            title = item.mediaMetadata.title?.toString(),
                            artist = item.mediaMetadata.artist?.toString(),
                            album = item.mediaMetadata.albumTitle?.toString(),
                            artwork = item.mediaMetadata.artworkUri?.toString()
                        )

                        serviceScope.launch {
                            com.saregama.android.audioplayer.data
                                .ServiceLocator
                                .repository()
                                .enqueueDownload(t)
                        }

                        Futures.immediateFuture(
                            SessionResult(SessionResult.RESULT_SUCCESS)
                        )
                    }
                }

                else -> Futures.immediateFuture(
                    SessionResult(SessionError.ERROR_NOT_SUPPORTED)
                )
            }
        }

    }

/*    private fun refreshCommandButtons() {
        val mediaId = session.player.currentMediaItem?.mediaId ?: return
        session.setCustomLayout(buildCustomLayout(mediaId))
    }*/

    private fun refreshCommandButtonsForCurrent() {


        // Make sure we’re on main when touching the session
        if (Looper.myLooper() == Looper.getMainLooper()) {
            val currentId = session.player.currentMediaItem?.mediaId ?: return
            val isFavorite = favoriteIds.contains(currentId)
            val isDownloaded=downloadIds.contains(currentId)
            val layout = buildCustomLayout(isFavorite,isDownloaded)
            session.setCustomLayout(layout)
        } else {
            Handler(Looper.getMainLooper()).post {
                val currentId = session.player.currentMediaItem?.mediaId ?: return@post
                val isFavorite = favoriteIds.contains(currentId)
                val isDownloaded=downloadIds.contains(currentId)
                val layout = buildCustomLayout(isFavorite,isDownloaded)
                session.setCustomLayout(layout)
            }
        }
    }

    private fun buildCustomLayout(isFavourite: Boolean,isDownloaded: Boolean): List<CommandButton> {
        return listOf(getFavCommandButton(isFavourite),getDownloadCommandButton(isDownloaded))
    }

    private fun getFavCommandButton(isFavorite: Boolean): CommandButton {
     /*   val isFavorite = if (mediaId!=null) com.saregama.android.audioplayer.data
            .ServiceLocator
            .repository()
            .isFavoriteNow(mediaId) else false*/

        val likeCommandButton = CommandButton.Builder(if (isFavorite)ICON_HEART_FILLED else ICON_HEART_UNFILLED)
            .setSessionCommand(CMD_TOGGLE_FAVORITE)
            .setDisplayName("Favourite")
            .setExtras(Bundle.EMPTY) // Optional: extra info
            .build()
        return likeCommandButton
    }

    private fun getDownloadCommandButton(isDownloaded: Boolean): CommandButton {
       /* val isDownloaded = if(mediaId!=null)com.saregama.android.audioplayer.data
            .ServiceLocator
            .repository()
            .isDownloaded(mediaId) else false*/

//        val icon=Icon.createWithResource(baseContext,R.drawable.download).resId
//if (isDownloaded) android.R.drawable.stat_sys_download else R.drawable.download_done
        val iconRes = if (isDownloaded) R.drawable.download_done else R.drawable.download
        val icon = Icon.createWithResource(this, iconRes)
        val downloadButton = CommandButton.Builder(CommandButton.ICON_UNDEFINED)
            .setCustomIconResId(iconRes)
            .setSessionCommand(CMD_DOWNLOAD)
            .setDisplayName("Download")
            .setExtras(Bundle.EMPTY) // Optional: extra info
            .build()
        return downloadButton
    }
}
