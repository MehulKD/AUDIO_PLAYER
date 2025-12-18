package com.saregama.android.player.data

class DefaultSongRepository : SongRepository {

    // In real app: fetch from DB or API
    private val allSongs: List<Song> = (1..1000).map { i ->
        Song(
            id = "track_$i",
            title = "Song #$i",
            artist = "Artist ${(i % 10) + 1}",
            streamUrl = "https://webaudioapi.com/samples/audio-tag/chrono.mp3",
            artworkUrl = "https://s.saregama.tech/image/s/0/e/48/24/dhurandher_website_1920x700_1764742727.jpg"
//            artworkUrl = "https://images.unsplash.com/photo-1548458716-ffca63bb2af4?q=80&w=2090&auto=format&fit=crop&ixlib=rb-4.1.0&ixid=M3wxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8fA%3D%3D"
        )
    }

    override suspend fun loadInitialQueue(pageSize: Int): SongPage {
        return loadPage(offset = 0, pageSize = pageSize)
    }

    override suspend fun loadMoreForQueue(
        lastSongId: String?,
        nextOffset: Int?,
        pageSize: Int
    ): SongPage {
        // Prefer offset if we have it; otherwise compute from lastSongId
        val offset = nextOffset ?: run {
            if (lastSongId == null) 0
            else {
                val idx = allSongs.indexOfFirst { it.id == lastSongId }
                if (idx == -1) 0 else (idx + 1)
            }
        }
        return loadPage(offset, pageSize)
    }

    private fun loadPage(offset: Int, pageSize: Int): SongPage {
        if (offset >= allSongs.size) {
            return SongPage(emptyList(), nextOffset = null)
        }
        val to = minOf(offset + pageSize, allSongs.size)
        val slice = allSongs.subList(offset, to)
        val newOffset = if (to >= allSongs.size) null else to
        return SongPage(slice, newOffset)
    }
}