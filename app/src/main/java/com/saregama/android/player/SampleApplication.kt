package com.saregama.android.player

import android.app.Application
import com.saregama.android.audioplayer.AudioPlayer
import com.saregama.android.audioplayer.PlayerConfig
import com.saregama.android.audioplayer.model.OnMoreTracksRequested
import com.saregama.android.audioplayer.model.TrackPageRequest
import com.saregama.android.audioplayer.model.TrackPageResult
import com.saregama.android.player.data.DefaultSongRepository
import com.saregama.android.player.data.toTrack

class SampleApplication : Application() {

    val songRepository: DefaultSongRepository by lazy {
        DefaultSongRepository()
    }

    override fun onCreate() {
        super.onCreate()

        AudioPlayer.init(
            context = this,
            config = PlayerConfig(
                repository = PlayerConfig.RepositoryConfig(
                    passphraseProvider = { "sample-passphrase".toByteArray() }
                ),
                resolver = { ids ->
                    // optional: if library ever resolves tracks by IDs
                    songRepository // you can implement a findByIds here
                    // for now return emptyList()
                    emptyList()
                },
                initialPageSize = 20,
                onMoreTracksRequested = OnMoreTracksRequested { req ->
                    handleMoreTracksRequest(req)
                }
            )
        )
    }

    private suspend fun handleMoreTracksRequest(
        req: TrackPageRequest
    ): TrackPageResult {
        val pageSize = req.pageSize
        val lastTrackId = req.lastTrackId
        // Interpret nextPageKey as offset Int stored as string
        val nextOffset = req.nextPageKey?.toIntOrNull()

        val page = songRepository.loadMoreForQueue(
            lastSongId = lastTrackId,
            nextOffset = nextOffset,
            pageSize = pageSize
        )

        val tracks = page.songs.map { it.toTrack() }
        val nextPageKey = page.nextOffset?.toString()

        return TrackPageResult(
            tracks = tracks,
            nextPageKey = nextPageKey
        )
    }
}