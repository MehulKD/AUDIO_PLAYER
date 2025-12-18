package com.saregama.android.audioplayer.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.sqlcipher.database.SupportFactory

@Database(entities = [TrackEntity::class, FavoriteEntity::class, DownloadEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tracks(): TrackDao
    abstract fun favorites(): FavoriteDao
    abstract fun downloads(): DownloadDao
    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context, passphrase: ByteArray): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context, AppDatabase::class.java, "ap_encrypted.db")
//                .openHelperFactory(SupportFactory(passphrase))
                .fallbackToDestructiveMigration(false)
                .build()
                .also { INSTANCE = it }
        }
    }
}