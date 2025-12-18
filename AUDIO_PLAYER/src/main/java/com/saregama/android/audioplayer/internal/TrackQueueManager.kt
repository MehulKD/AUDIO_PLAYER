package com.saregama.android.audioplayer.internal

import com.saregama.android.audioplayer.model.Track
import com.saregama.android.audioplayer.model.TrackPageRequest
import com.saregama.android.audioplayer.model.TrackPageResult
import com.saregama.android.audioplayer.model.OnMoreTracksRequested
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TrackQueueManager(
    private val scope: CoroutineScope,
    private val pageSize: Int,
    private val onMoreTracksRequested: OnMoreTracksRequested?
) {

    private val mutex = Mutex()
    private val _queue = mutableListOf<Track>()
    private var _currentIndex = -1

    private var nextPageKey: String? = null
    private var noMorePages: Boolean = false
    private var isLoadingMore: Boolean = false

    val queue: List<Track> get() = _queue
    val currentIndex: Int get() = _currentIndex

    suspend fun setInitialQueue(tracks: List<Track>, nextPageKey: String?) {
        mutex.withLock {
            _queue.clear()
            _queue.addAll(tracks)
            _currentIndex = if (tracks.isEmpty()) -1 else 0
            this.nextPageKey = nextPageKey
            noMorePages = false
        }
    }

    suspend fun moveToNext(): Track? {
        return mutex.withLock {
            val nextIndex = _currentIndex + 1
            if (nextIndex < _queue.size) {
                _currentIndex = nextIndex
                _queue[_currentIndex]
            } else {
                null // no immediate next – caller can trigger loadMoreIfNeeded
            }
        }
    }

    suspend fun current(): Track? = mutex.withLock {
        if (_currentIndex in _queue.indices) _queue[_currentIndex] else null
    }

    /**
     * Call this when playback is near the end or when you tried to moveNext and got null.
     */
    fun loadMoreIfNeeded() {
        if (onMoreTracksRequested == null) return
        if (noMorePages || isLoadingMore) return

        scope.launch {
            mutex.withLock {
                if (noMorePages || isLoadingMore) return@launch
                isLoadingMore = true
            }

            val lastTrackId = mutex.withLock {
                _queue.lastOrNull()?.id
            }

            val request = TrackPageRequest(
                lastTrackId = lastTrackId,
                nextPageKey = nextPageKey,
                pageSize = pageSize
            )

            val result: TrackPageResult = try {
                onMoreTracksRequested.loadMore(request)
            } catch (t: Throwable) {
                // TODO log error
                TrackPageResult(emptyList(), nextPageKey)
            }

            mutex.withLock {
                if (result.tracks.isNotEmpty()) {
                    _queue.addAll(result.tracks)
                }
                nextPageKey = result.nextPageKey
                if (result.tracks.isEmpty() || result.nextPageKey == null) {
                    noMorePages = true
                }
                isLoadingMore = false
            }
        }
    }
}
