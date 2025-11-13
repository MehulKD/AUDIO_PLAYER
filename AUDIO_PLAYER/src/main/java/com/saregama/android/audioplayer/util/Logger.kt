package com.saregama.android.audioplayer.util

interface Logger { fun d(tag: String, msg: String): Int; fun e(tag: String, t: Throwable): Int }
object AndroidLogger : Logger {
    override fun d(tag: String, msg: String) = android.util.Log.d(tag, msg)
    override fun e(tag: String, t: Throwable) = android.util.Log.e(tag, t.message, t)
}
