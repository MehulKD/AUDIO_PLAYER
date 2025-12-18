package com.saregama.android.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("AudioPlayer Sample") }
        )

        if (state.isLoadingInitial) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            SongList(
                state = state,
                onSongClick = { viewModel.onSongClicked(it) },
                onPlayPause = { viewModel.onPlayPause(it) },
                onFav = { viewModel.onToggleFavorite(it) },
                onDownload = { viewModel.onDownload(it) },
                onNeedMore = { lastIndex -> viewModel.loadMoreIfNeeded(lastIndex) }
            )
        }
    }
}

@Composable
private fun SongList(
    state: MainUiState,
    onSongClick: (SongUi) -> Unit,
    onPlayPause: (SongUi) -> Unit,
    onFav: (SongUi) -> Unit,
    onDownload: (SongUi) -> Unit,
    onNeedMore: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(state.songs, key = { _, song -> song.id }) { index, song ->
            // Trigger pagination when near bottom
            LaunchedEffect(index) {
                onNeedMore(index)
            }

            SongRow(
                song = song,
                onPlayPause = { onPlayPause(song) },
                onFav = { onFav(song) },
                onDownload = { onDownload(song) },
                onClick = { onSongClick(song) }
            )
            Divider()
        }

        item {
            if (state.isLoadingMore) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: SongUi,
    onPlayPause: () -> Unit,
    onFav: () -> Unit,
    onDownload: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlayPause) {
            if (song.isCurrent && song.isPlaying) {
                Icon(Icons.Default.Pause, contentDescription = "Pause")
            } else {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.downloadStatus == 1) { // downloading / queued
                LinearProgressIndicator(
                    progress = (song.downloadProgress / 100f).coerceIn(0f, 1f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            } else if (song.downloadStatus == 2) {
                // maybe show a small "Downloaded" label/icon

            }
        }

        IconButton(onClick = onFav) {
            Icon(
                imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (song.isFavorite) "Unfavorite" else "Favorite",
                tint = if (song.isFavorite) Color.Red else LocalContentColor.current
            )
        }

        IconButton(
            onClick = { if (song.downloadStatus == 0 || song.downloadStatus == -1) onDownload() },
            enabled = song.downloadStatus != 1 // disable while downloading
        ) {
            when (song.downloadStatus) {

                0 -> Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Download"
                )

                1 -> {
                    // show progress circle
                    CircularProgressIndicator(
                        progress = (song.downloadProgress / 100f).coerceIn(0f, 1f),
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }

                2 -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = Color.Green
                )

                -1 -> Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Retry download",
                    tint = Color.Red
                )
            }
        }

    }
}