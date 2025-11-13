package com.saregama.android.audioplayer.data

import android.content.Context
import com.saregama.android.audioplayer.db.AppDatabase

object DatabaseProvider {
    @Volatile private var appContext: Context? = null
    @Volatile private var passphrase: ByteArray? = null
    @Volatile private var db: AppDatabase? = null

    fun isReady(): Boolean = db != null

    fun init(ctx: Context, key: ByteArray) {
        if (db != null) return
        synchronized(this) {
            if (db == null) {
                appContext = ctx.applicationContext
                passphrase = key
                db = AppDatabase.get(appContext!!, key)
            }
        }
    }

    fun database(): AppDatabase =
        db ?: error("DatabaseProvider not initialized")

    fun passphrase(): ByteArray =
        passphrase ?: error("DatabaseProvider not initialized")
}