package com.saregama.android.audioplayer.service

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.saregama.android.audioplayer.model.Track

internal fun Track.toMediaItem(): MediaItem {
    val b = MediaItem.Builder().setMediaId(id).setUri(url)
    val md = androidx.media3.common.MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setArtworkUri(artwork?.let { android.net.Uri.parse(it) })
        .build()
    b.setMediaMetadata(md)

        val extras = Bundle().apply { for ((k, v) in headers) putString(k, v) }
        b.setTag(extras)
    return b.build()
}
