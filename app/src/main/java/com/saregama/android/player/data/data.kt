package com.saregama.android.player.data

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val streamUrl: String,
    val artworkUrl: String?
)

data class SongPage(
    val songs: List<Song>,
    val nextOffset: Int?,      // null = no more songs
)