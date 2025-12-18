package com.saregama.android.player.data


interface SongRepository {

    /**
     * Load first batch for a queue (e.g. when user presses "Play All").
     */
    suspend fun loadInitialQueue(pageSize: Int): SongPage

    /**
     * Load more songs after the current batch.
     *
     * @param lastSongId  id of last song currently in the queue (may be null for initial)
     * @param nextOffset  offset you got from previous page (null for initial)
     */
    suspend fun loadMoreForQueue(
        lastSongId: String?,
        nextOffset: Int?,
        pageSize: Int
    ): SongPage
}