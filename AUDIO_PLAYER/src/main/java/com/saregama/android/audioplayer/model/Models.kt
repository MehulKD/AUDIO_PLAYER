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

data class DownloadState(
    val trackId: String,
    val status: DownloadStatus,
    val progress: Int  // 0..100
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}


data class TrackPageRequest(
    val lastTrackId: String?,   // last track currently in queue (or null for first page)
    val nextPageKey: String?,   // opaque key the app can use for pagination (offset, token, etc.)
    val pageSize: Int           // how many new tracks we’d like (e.g. 20)
)

data class TrackPageResult(
    val tracks: List<Track>,
    val nextPageKey: String?,   // null = no more pages
)


sealed interface PlaybackEvent {
    data class StateChanged(val state: PlaybackState): PlaybackEvent
    data class TrackChanged(val index: Int): PlaybackEvent
    data class QueueEnded(val reason: Int): PlaybackEvent
    data class Error(val throwable: Throwable): PlaybackEvent
}

fun interface OnMoreTracksRequested {
    suspend fun loadMore(request: TrackPageRequest): TrackPageResult
}
