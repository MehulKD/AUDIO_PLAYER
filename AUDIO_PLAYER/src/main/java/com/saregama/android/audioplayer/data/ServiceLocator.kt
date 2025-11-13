package com.saregama.android.audioplayer.data


import android.content.Context
import com.saregama.android.audioplayer.db.AppDatabase

object ServiceLocator {
    @Volatile private var repo: AudioRepository? = null

    fun ensureInitialized(context: Context) {
        if (repo != null) return
        synchronized(this) {
            if (repo == null) {
                val db = DatabaseProvider.database()
                repo = AudioRepository(context.applicationContext, db)
            }
        }
    }

    fun repository(): AudioRepository =
        repo ?: error("ServiceLocator not initialized")
}