package com.saregama.android.audioplayer.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saregama.android.audioplayer.model.DownloadState

@Dao
interface TrackDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(vararg t: TrackEntity)

    @Query("SELECT * FROM tracks WHERE id = :id") suspend fun get(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)") suspend fun findByIds(ids: List<String>): List<TrackEntity>
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(f: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE trackId = :trackId") suspend fun remove(trackId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)") suspend fun isFavorite(trackId: String): Boolean

    @Query("SELECT COUNT(*) FROM favorites WHERE trackId = :id")
    fun isFavoriteNow(id: String): Int

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE trackId = :trackId)")
    fun observeIsFavorite(trackId: String): kotlinx.coroutines.flow.Flow<Boolean>

    @Query("SELECT * FROM favorites")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<FavoriteEntity>>
}

@Dao
interface DownloadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(d: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE trackId = :trackId") suspend fun get(trackId: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE trackId = :trackId")
    fun observeDownload(trackId: String): kotlinx.coroutines.flow.Flow<DownloadEntity?>

    @Query("SELECT trackId, status, progress FROM downloads")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<DownloadState>>

    @Query("SELECT COUNT(*) FROM downloads WHERE trackId = :trackId")
    fun isDownloaded(trackId: String): Int
}