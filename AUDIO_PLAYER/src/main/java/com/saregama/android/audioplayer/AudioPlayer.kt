package com.saregama.android.audioplayer

import android.content.Context
import androidx.annotation.MainThread
import com.saregama.android.audioplayer.data.ServiceLocator
import com.saregama.android.audioplayer.model.DownloadState
import com.saregama.android.audioplayer.model.PlaybackEvent
import com.saregama.android.audioplayer.model.PlaybackState
import com.saregama.android.audioplayer.model.Track
import com.saregama.android.audioplayer.service.ServiceConnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/*
* AudioPlayer.init(
    appContext,
    PlayerConfig(
        repository = PlayerConfig.RepositoryConfig(
            passphraseProvider = {
                // e.g., load from Android Keystore or derive once and cache
                "my-super-secret-passphrase".toByteArray()
            }
        ),
        resolver = { ids -> ServiceLocator.repository().findByIds(ids.map { it.value }) }
    )
)
* */

/*
//Collect in UI (Compose / lifecycle)
val repo = ServiceLocator.repository()
val isFav by repo.observeIsFavorite(currentTrackId)
    .collectAsState(initial = false)

val dlStatus by repo.observeDownload(currentTrackId)
    .collectAsState(initial = null)
* */
object AudioPlayer {
    @Volatile private var connector: ServiceConnector? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @MainThread
    fun init(context: Context, config: PlayerConfig = PlayerConfig()) {
        if (connector != null) return

        // NEW: bootstrap DB + repository if requested
        config.repository?.let { repoCfg ->
            if (!com.saregama.android.audioplayer.data.DatabaseProvider.isReady() && repoCfg.autoInit) {
                // Resolve passphrase (allowing Keystore/async work)
                kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val key = repoCfg.passphraseProvider()
                    com.saregama.android.audioplayer.data.DatabaseProvider.init(context, key)
                    com.saregama.android.audioplayer.data.ServiceLocator.ensureInitialized(context)
                }
            }
        }

        connector = ServiceConnector(
            context.applicationContext, config
        ).also { it.bind() }
    }

    fun isInitialized(): Boolean = connector != null

    suspend fun setQueue(
        tracks: List<Track>,
        startIndex: Int = 0,
        playWhenReady: Boolean = false,
        nextPageKey: String? = null        // ⬅️ NEW
    ) = requireConnector().setQueue(
        tracks = tracks,
        startIndex = startIndex,
        playWhenReady = playWhenReady,
        nextPageKey = nextPageKey          // ⬅️ pass to connector/service
    )

    suspend fun add(tracks: List<Track>) = requireConnector().add(tracks)
    suspend fun removeAt(index: Int) = requireConnector().removeAt(index)
    suspend fun clearQueue() = requireConnector().clearQueue()

    fun play() = requireConnector().play()
    fun pause() = requireConnector().pause()
    fun stop() = requireConnector().stop()
    fun seekTo(positionMs: Long) = requireConnector().seekTo(positionMs)
    fun skipToNext() = requireConnector().skipToNext()
    fun skipToPrevious() = requireConnector().skipToPrevious()
    fun setRepeatMode(mode: Int) = requireConnector().setRepeatMode(mode)
    fun setShuffleMode(enabled: Boolean) = requireConnector().setShuffleMode(enabled)

    fun currentState(): PlaybackState = requireConnector().currentState()



    // Favorites stream
    val favorites: Flow<Set<String>>
        get() = ServiceLocator.repository().observeFavorites()

    // Toggle favorite from app / anywhere
    fun toggleFavorite(trackId: String) {
        scope.launch {
            ServiceLocator.repository().toggleFavorite(trackId)
        }
    }

    fun enqueueDownload(track: Track) {
        scope.launch {
            ServiceLocator
                .repository()
                .enqueueDownload(track)
        }
    }
    /**
     * Convenience: download by id + url + meta if you don’t have a Track instance.
     */
    fun enqueueDownload(
        id: String,
        url: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        artwork: String? = null
    ) {
        val t = Track(
            id = id,
            url = url,
            title = title,
            artist = artist,
            album = album,
            artwork = artwork
        )
        enqueueDownload(t)
    }

    val downloads: Flow<List<DownloadState>> get() = ServiceLocator.repository().observeDownloads()

    fun addListener(listener: (PlaybackEvent) -> Unit) = requireConnector().addListener(listener)
    fun removeListener(listener: (PlaybackEvent) -> Unit) = requireConnector().removeListener(listener)

    fun release() { connector?.release(); connector = null }

    private fun requireConnector(): ServiceConnector =
        connector ?: error("AudioPlayer not initialized. Call AudioPlayer.init(context) first.")
}
