package com.saregama.android.audioplayer.data

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.saregama.android.audioplayer.db.*
import com.saregama.android.audioplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    // Called by TOGGLE_FAVORITE action
    suspend fun toggleFavorite(trackId: String): Boolean = withContext(Dispatchers.IO) {
        val favDao = db.favorites()
        val isFav = favDao.isFavorite(trackId)
        if (isFav) favDao.remove(trackId) else favDao.add(FavoriteEntity(trackId))
        !isFav
    }

    // Called by DOWNLOAD action
    fun enqueueDownload(track: Track) {
        val wm = WorkManager.getInstance(context)
        val data = Data.Builder()
            .putString("trackId", track.id)
            .putString("url", track.url)
            .putString("title", track.title ?: "")
            .putString("artist", track.artist ?: "")
            .putString("album", track.album ?: "")
            .putString("artwork", track.artwork ?: "")
            .build()

        val req = OneTimeWorkRequestBuilder<com.saregama.android.audioplayer.download.DownloadWorker>()
            .setInputData(data)
            .build()

        wm.enqueue(req)
    }

    // Optional helpers (useful for restore/search/seed)
    suspend fun upsertTracks(tracks: List<Track>) = withContext(Dispatchers.IO) {
        db.tracks().upsert(*tracks.map { it.toEntity() }.toTypedArray())
    }

    suspend fun findByIds(ids: List<String>): List<Track> = withContext(Dispatchers.IO) {
        db.tracks().findByIds(ids).map { it.toModel() }
    }

    fun observeIsFavorite(trackId: String) = db.favorites().observeIsFavorite(trackId)

    fun observeDownload(trackId: String) = db.downloads().observeDownload(trackId)
}

// Mappers
private fun Track.toEntity() = TrackEntity(
    id = id, url = url, title = title, artist = artist, album = album,
    artworkUrl = artwork, localFilePath = null, artworkFilePath = null,
    durationMs = null, mimeType = null
)

private fun TrackEntity.toModel() = Track(
    id = id, url = url, title = title, artist = artist, album = album, artwork = artworkUrl
)