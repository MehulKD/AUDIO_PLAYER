package com.saregama.android.audioplayer.service

import android.content.Context
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

internal object CacheHolder {
    @Volatile private var cache: SimpleCache? = null
    fun get(context: Context): SimpleCache {
        return cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(context.cacheDir, "media3_cache"),
                LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024)
            ).also { cache = it }
        }
    }
}
