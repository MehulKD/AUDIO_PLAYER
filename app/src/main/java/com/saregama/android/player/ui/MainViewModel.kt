package com.saregama.android.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saregama.android.audioplayer.AudioPlayer
import com.saregama.android.audioplayer.data.ServiceLocator
import com.saregama.android.audioplayer.model.DownloadState
import com.saregama.android.audioplayer.model.DownloadStatus
import com.saregama.android.audioplayer.model.PlaybackEvent
import com.saregama.android.audioplayer.model.PlaybackState
import com.saregama.android.audioplayer.model.Track
import com.saregama.android.player.SampleApplication
import com.saregama.android.player.data.SongRepository
import com.saregama.android.player.data.toTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: SongRepository =
        (app as SampleApplication).songRepository

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState



    private var nextOffset: Int? = null
    private var endReached: Boolean = false
    private var isLoading: Boolean = false

    private var currentTrackId: String? = null
    private var isPlaying: Boolean = false

    private val pageSize = 20

    private val playerListener: (PlaybackEvent) -> Unit = { event ->
        when (event) {
            is PlaybackEvent.StateChanged -> applyStateChanged(event.state)
            is PlaybackEvent.TrackChanged -> applyTrackChanged(event.index)
            is PlaybackEvent.QueueEnded -> handleQueueEnded(event.reason)
            is PlaybackEvent.Error -> handleError(event.throwable)
        }
    }

    override fun onCleared() {
        AudioPlayer.removeListener(playerListener)
        super.onCleared()
    }

    init {
        loadInitial()
        AudioPlayer.addListener(playerListener)
        viewModelScope.launch {
            AudioPlayer.downloads.collect { downloadStates ->
                applyDownloadStates(downloadStates)
            }
        }
        // 🔥 Collect favorites from library
        viewModelScope.launch {
            AudioPlayer.favorites.collect { favoriteIds ->
                applyFavoriteStates(favoriteIds)
            }
        }
    }

    private fun loadInitial() {
        if (isLoading) return
        isLoading = true
        _uiState.update { it.copy(isLoadingInitial = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val page = repo.loadInitialQueue(pageSize)
                nextOffset = page.nextOffset
                endReached = page.nextOffset == null

                val list = page.songs.map {
                    SongUi(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        streamUrl = it.streamUrl
                    )
                }

                _uiState.value = MainUiState(
                    songs = list,
                    isLoadingInitial = false,
                    isLoadingMore = false,
                    errorMessage = null
                )
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoadingInitial = false,
                        errorMessage = t.message ?: "Failed to load songs"
                    )
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun loadMoreIfNeeded(lastVisibleIndex: Int) {
        val state = _uiState.value
        if (isLoading || endReached || state.isLoadingInitial) return

        // If last visible is within last 3 items, load more
        if (lastVisibleIndex >= state.songs.size - 3) {
            loadMore()
        }
    }

    private fun loadMore() {
        if (isLoading || endReached) return
        isLoading = true
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            try {
                val stateBefore = _uiState.value
                val lastId = stateBefore.songs.lastOrNull()?.id
                val page = repo.loadMoreForQueue(
                    lastSongId = lastId,
                    nextOffset = nextOffset,
                    pageSize = pageSize
                )

                nextOffset = page.nextOffset
                endReached = page.nextOffset == null

                val more = page.songs.map {
                    SongUi(
                        id = it.id,
                        title = it.title,
                        artist = it.artist,
                        streamUrl = it.streamUrl
                    )
                }

                _uiState.update { old ->
                    old.copy(
                        songs = old.songs + more,
                        isLoadingMore = false
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoadingMore = false,
                        errorMessage = t.message ?: "Failed to load more"
                    )
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun onSongClicked(song: SongUi) {
        viewModelScope.launch {
            // Build queue from current UI songs
            val state = _uiState.value
            val index = state.songs.indexOfFirst { it.id == song.id }
            if (index == -1) return@launch

            val tracks = repo.loadInitialQueue(pageSize = state.songs.size).songs.map { it.toTrack() }
            val nextKey = nextOffset?.toString()

            // TODO: ensure your AudioPlayer.setQueue signature has nextPageKey
            AudioPlayer.setQueue(
                tracks = tracks,
                startIndex = index,
                playWhenReady = true,
                nextPageKey = nextKey
            )

            currentTrackId = song.id
            isPlaying = true
            updatePlayingFlags()
        }
    }

    fun onPlayPause(song: SongUi) {
        viewModelScope.launch {
            if (currentTrackId == song.id) {
                // toggle
                if (isPlaying) {
                    AudioPlayer.pause()
                    isPlaying = false
                } else {
                    AudioPlayer.play()
                    isPlaying = true
                }
                updatePlayingFlags()
            } else {
                onSongClicked(song)
            }
        }
    }

    fun onToggleFavorite(song: SongUi) {
        // Let the library handle DB + side effects
        AudioPlayer.toggleFavorite(song.id)

        // Optional optimistic UI (not required, Flow will update anyway)
        _uiState.update { state ->
            state.copy(
                songs = state.songs.map {
                    if (it.id == song.id) it.copy(isFavorite = !it.isFavorite) else it
                }
            )
        }
    }

    fun onDownload(song: SongUi) {
        // TODO: call into library's enqueueDownload(Track) API
        // For UI demo, mark as downloading then downloaded
       /* _uiState.update { state ->
            state.copy(
                songs = state.songs.map {
                    if (it.id == song.id) it.copy(
                        downloadStatus = 1,
                        downloadProgress = 0
                    ) else it
                }
            )
        }*/

        // In reality, you'd listen to DownloadWorker progress via DB Flow/WorkManager
        val track = Track(
            id = song.id,
            url = song.streamUrl,  // make sure you have this
            title = song.title,
            artist = song.artist,
            album = null,
            artwork = null
        )
        AudioPlayer.enqueueDownload(track)
    }

    private fun applyFavoriteStates(favoriteIds: Set<String>) {
        _uiState.update { state ->
            state.copy(
                songs = state.songs.map { song ->
                    song.copy(
                        isFavorite = favoriteIds.contains(song.id)
                    )
                }
            )
        }
    }


    private fun applyDownloadStates(downloadStates: List<DownloadState>) {
        _uiState.update { state ->
            state.copy(
                songs = state.songs.map { song ->
                    val d = downloadStates.find { it.trackId == song.id }
                    if (d != null) {
                        song.copy(
                            downloadStatus = when (d.status) {
                                DownloadStatus.NOT_DOWNLOADED -> 0
                                DownloadStatus.QUEUED -> 1
                                DownloadStatus.DOWNLOADING -> 1
                                DownloadStatus.COMPLETED -> 2
                                DownloadStatus.FAILED -> -1
                            },
                            downloadProgress = d.progress
                        )
                    } else {
                        song
                    }
                }
            )
        }
    }


    private fun updatePlayingFlags() {
        val stateNow = _uiState.value
        _uiState.value = stateNow.copy(
            songs = stateNow.songs.map { song ->
                val isCurrent = song.id == currentTrackId
                song.copy(
                    isCurrent = isCurrent,
                    isPlaying = isCurrent && isPlaying
                )
            }
        )
    }

    private fun applyTrackChanged(index: Int) {
        _uiState.update { state ->
            state.copy(
                songs = state.songs.mapIndexed { idx, song ->
                    song.copy(
                        isCurrent = idx == index,
                        // playing flag will be set by StateChanged
                        isPlaying = if (idx == index) song.isPlaying else false
                    )
                }
            )
        }
    }

    private fun applyStateChanged(state: PlaybackState) {
        _uiState.update { ui ->
            val size = ui.songs.size
            val idx = state.currentIndex.coerceIn(0, maxOf(size - 1, 0))
            val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f

            ui.copy(
                songs = ui.songs.mapIndexed { i, song ->
                    val isCurrent = i == idx
                    song.copy(
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && state.isPlaying
                    )
                }
            )
        }
    }

    private fun handleQueueEnded(reason: Int) {
        // e.g. you can ask repository for more songs here using your paging API
        // or just log / no-op for now
    }

    private fun handleError(throwable: Throwable) {
        _uiState.update { state ->
            state.copy(errorMessage = throwable.message ?: "Playback error")
        }
    }
}