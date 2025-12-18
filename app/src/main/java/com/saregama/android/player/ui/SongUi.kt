package com.saregama.android.player.ui


data class SongUi(
    val id: String,
    val title: String,
    val artist: String,
    val streamUrl: String,
    val isFavorite: Boolean = false,
    val isCurrent: Boolean = false,
    val isPlaying: Boolean = false,
    val downloadStatus: Int = 0,   // -1 fail, 0 none, 1 downloading, 2 downloaded
    val downloadProgress: Int = 0  // 0..100
)

data class MainUiState(
    val songs: List<SongUi> = emptyList(),
    val isLoadingInitial: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null
)