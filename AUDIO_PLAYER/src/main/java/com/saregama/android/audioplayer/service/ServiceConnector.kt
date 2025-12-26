package com.saregama.android.audioplayer.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.saregama.android.audioplayer.PlayerConfig
import com.saregama.android.audioplayer.model.PlaybackEvent
import com.saregama.android.audioplayer.model.PlaybackState
import com.saregama.android.audioplayer.model.Track
import com.saregama.android.audioplayer.state.PlaybackStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

internal class ServiceConnector(private val appContext: Context, private val config: PlayerConfig) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val eventFlow = MutableSharedFlow<PlaybackEvent>(replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var controller: MediaController? = null

    fun bind() {
        val token = SessionToken(appContext, ComponentName(appContext, AudioPlayerService::class.java))
        scope.launch {
            val mc = MediaController.Builder(appContext, token).buildAsync().await()
            controller = mc
            wirePlayerCallbacks(mc)
            restoreIfAny()
        }
    }

    fun release() { controller?.release(); controller = null; scope.cancel() }

    private fun wirePlayerCallbacks(mc: MediaController) {
        mc.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                val s = currentState()
                eventFlow.tryEmit(PlaybackEvent.StateChanged(s))
                scope.launch {
                    PlaybackStateStore.save(appContext, s.currentIndex, s.positionMs, currentQueueIds())
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                eventFlow.tryEmit(PlaybackEvent.StateChanged(currentState()))
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                eventFlow.tryEmit(PlaybackEvent.TrackChanged(mc.currentMediaItemIndex))
            }
        })
    }

    // Queue APIs
    suspend fun setQueue(
        tracks: List<Track>,
        startIndex: Int,
        playWhenReady: Boolean,
        nextPageKey: String?=null                    // ⬅️ NEW
    ) {
        // store paging cursor so the service can request more later
        QueuePagingState.nextPageKey = nextPageKey

        val mc = controller ?: return
        mc.stop()
        mc.clearMediaItems()
        mc.setMediaItems(tracks.map { it.toMediaItem() })
        mc.prepare()
        mc.seekTo(startIndex, 0)
        mc.playWhenReady = playWhenReady
    }

    suspend fun add(tracks: List<Track>) { controller?.addMediaItems(tracks.map { it.toMediaItem() }) }
    suspend fun removeAt(index: Int) { controller?.removeMediaItem(index) }
    suspend fun clearQueue() { controller?.clearMediaItems() }

    // Playback APIs
    fun play() { controller?.play() }
    fun pause() { controller?.pause() }
    fun stop() { controller?.stop() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }
    fun skipToNext() { controller?.seekToNext() }
    fun skipTo(index: Int) { controller?.seekTo(index,0) }
    fun skipToPrevious() { controller?.seekToPrevious() }
    fun setRepeatMode(mode: Int) { controller?.repeatMode = mode }
    fun setShuffleMode(enabled: Boolean) { controller?.shuffleModeEnabled = enabled }

    fun currentState(): PlaybackState {
        val mc = controller ?: return PlaybackState(false, Player.STATE_IDLE, 0, 0, 0, 0, false, Player.REPEAT_MODE_OFF)
        return PlaybackState(
            isPlaying = mc.isPlaying,
            playbackState = mc.playbackState,
            currentIndex = mc.currentMediaItemIndex,
            durationMs = maxOf(0, mc.duration),
            positionMs = maxOf(0, mc.currentPosition),
            bufferedMs = maxOf(0, mc.bufferedPosition),
            shuffleEnabled = mc.shuffleModeEnabled,
            repeatMode = mc.repeatMode,
        )
    }

    fun addListener(listener: (PlaybackEvent) -> Unit) { scope.launch { eventFlow.collect { listener(it) } } }
    fun removeListener(listener: (PlaybackEvent) -> Unit) { /* simple */ }

    private fun currentQueueIds(): List<String> {
        val mc = controller ?: return emptyList()
        return (0 until mc.mediaItemCount).map { idx -> mc.getMediaItemAt(idx).mediaId }
    }

    private suspend fun restoreIfAny() {
        val (index, position, ids) = PlaybackStateStore.load(appContext)
        if (ids.isNotEmpty() && config.resolver != null) {
            val tracks = config.resolver.invoke(ids)
            if (tracks.isNotEmpty()) {
                setQueue(tracks, index.coerceIn(0, tracks.lastIndex), playWhenReady = false)
                seekTo(position)
            }
        }
    }
}
