package com.saregama.android.audioplayer

import android.content.Context
import androidx.annotation.MainThread
import com.saregama.android.audioplayer.model.PlaybackEvent
import com.saregama.android.audioplayer.model.PlaybackState
import com.saregama.android.audioplayer.model.Track
import com.saregama.android.audioplayer.service.ServiceConnector
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

        connector = com.saregama.android.audioplayer.service.ServiceConnector(
            context.applicationContext, config
        ).also { it.bind() }
    }

    fun isInitialized(): Boolean = connector != null

    suspend fun setQueue(tracks: List<Track>, startIndex: Int = 0, playWhenReady: Boolean = false) =
        requireConnector().setQueue(tracks, startIndex, playWhenReady)

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

    fun addListener(listener: (PlaybackEvent) -> Unit) = requireConnector().addListener(listener)
    fun removeListener(listener: (PlaybackEvent) -> Unit) = requireConnector().removeListener(listener)

    fun release() { connector?.release(); connector = null }

    private fun requireConnector(): ServiceConnector =
        connector ?: error("AudioPlayer not initialized. Call AudioPlayer.init(context) first.")
}
