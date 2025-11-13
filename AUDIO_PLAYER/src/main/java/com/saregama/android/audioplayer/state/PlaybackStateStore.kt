package com.saregama.android.audioplayer.state

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("audio_state")

object PlaybackStateStore {
    private val KEY_INDEX = intPreferencesKey("index")
    private val KEY_POSITION = longPreferencesKey("position")
    private val KEY_QUEUE = stringSetPreferencesKey("queue_ids")

    suspend fun save(context: Context, index: Int, position: Long, queue: List<String>) {
        context.dataStore.edit {
            it[KEY_INDEX] = index
            it[KEY_POSITION] = position
            it[KEY_QUEUE] = queue.toSet()
        }
    }

    suspend fun load(context: Context): Triple<Int, Long, List<String>> {
        val p = context.dataStore.data.first()
        return Triple(p[KEY_INDEX] ?: 0, p[KEY_POSITION] ?: 0L, p[KEY_QUEUE]?.toList() ?: emptyList())
    }
}
