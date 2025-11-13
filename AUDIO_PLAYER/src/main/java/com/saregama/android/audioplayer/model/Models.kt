package com.saregama.android.audioplayer.model

 data class Track(
    val id: String,
    val url: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val artwork: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

data class PlaybackState(
    val isPlaying: Boolean,
    val playbackState: Int,
    val currentIndex: Int,
    val durationMs: Long,
    val positionMs: Long,
    val bufferedMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
)

sealed interface PlaybackEvent {
    data class StateChanged(val state: PlaybackState): PlaybackEvent
    data class TrackChanged(val index: Int): PlaybackEvent
    data class QueueEnded(val reason: Int): PlaybackEvent
    data class Error(val throwable: Throwable): PlaybackEvent
}
