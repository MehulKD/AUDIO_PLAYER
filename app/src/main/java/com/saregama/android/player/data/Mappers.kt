package com.saregama.android.player.data

import com.saregama.android.audioplayer.model.Track

fun Song.toTrack(): Track =
    Track(
        id = id,
        url = streamUrl,
        title = title,
        artist = artist,
        album = null,
        artwork = artworkUrl
    )