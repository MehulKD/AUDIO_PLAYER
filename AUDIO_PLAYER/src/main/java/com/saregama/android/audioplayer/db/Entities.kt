package com.saregama.android.audioplayer.db

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val artworkUrl: String?,
    val localFilePath: String?,   // if downloaded
    val artworkFilePath: String?, // if downloaded
    val durationMs: Long?,
    val mimeType: String?,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val trackId: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val trackId: String,
    val status: Int,   // 0 queued, 1 running, 2 success, -1 failed
    val progress: Int, // 0..100
    val filePath: String?,
    val artworkPath: String?,
    val error: String?
)