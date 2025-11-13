// com/saregama/android/audioplayer/download/DownloadWorker.kt
package com.saregama.android.audioplayer.download

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.saregama.android.audioplayer.data.DatabaseProvider
import com.saregama.android.audioplayer.db.DownloadEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.min

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork() = withContext(Dispatchers.IO) {
        val trackId = inputData.getString("trackId") ?: return@withContext Result.failure()
        val urlStr  = inputData.getString("url")     ?: return@withContext Result.failure()
        val artwork = inputData.getString("artwork")

        val db = DatabaseProvider.database()

        db.downloads().upsert(DownloadEntity(trackId, 1, 0, null, null, null)) // running

        try {
            val audioDir = applicationContext.getExternalFilesDir("audio")!!.apply { mkdirs() }
            val audioFile = File(audioDir, "$trackId.mp3")

            // suspend progress callback -> safe to call suspend DAO
            downloadWithProgress(
                url = urlStr,
                outFile = audioFile,
                onProgress = { pct ->
                    db.downloads().upsert(
                        DownloadEntity(
                            trackId = trackId,
                            status = 1,
                            progress = pct,
                            filePath = null,
                            artworkPath = null,
                            error = null
                        )
                    )
                    setProgress(workDataOf("trackId" to trackId, "progress" to pct))
                }
            )

            var artPath: String? = null
            if (!artwork.isNullOrBlank()) {
                val artDir = applicationContext.getExternalFilesDir("art")!!.apply { mkdirs() }
                val artFile = File(artDir, "$trackId.jpg")
                runCatching { URL(artwork).openStream().use { it.copyTo(artFile.outputStream()) } }
                if (artFile.exists() && artFile.length() > 0) artPath = artFile.absolutePath
            }

            db.downloads().upsert(DownloadEntity(trackId, 2, 100, audioFile.absolutePath, artPath, null))
            Result.success()
        } catch (t: Throwable) {
            db.downloads().upsert(DownloadEntity(trackId, -1, 0, null, null, t.message))
            Result.failure()
        }
    }


    /**
     * Streams the url to [outFile] and calls [onProgress] with 0..100.
     * Throttles callbacks to avoid excessive DB writes.
     */
    private suspend fun downloadWithProgress(
        url: String,
        outFile: File,
        onProgress: suspend (Int) -> Unit
    ) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }

        conn.inputStream.use { input ->
            val totalBytes = max(0L, conn.contentLengthLong)
            val buf = ByteArray(128 * 1024)
            val tmp = File(outFile.parentFile, outFile.name + ".part")
            var downloaded = 0L
            var lastEmitPct = -1
            var lastEmitTime = 0L
            val throttleMs = 250L

            onProgress(0)

            tmp.outputStream().use { output ->
                while (true) {
                    if (!coroutineContext.isActive) {
                        conn.disconnect()
                        throw InterruptedException("Worker cancelled")
                    }
                    val read = input.read(buf)
                    if (read == -1) break
                    output.write(buf, 0, read)
                    downloaded += read

                    val pct = if (totalBytes > 0L)
                        ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                    else -1

                    val now = System.currentTimeMillis()
                    val shouldEmit =
                        (pct >= 0 && pct != lastEmitPct && now - lastEmitTime >= throttleMs) ||
                                (pct < 0 && now - lastEmitTime >= throttleMs)

                    if (shouldEmit) {
                        val emit = if (pct >= 0) pct else (min(99, (lastEmitPct + 1).coerceAtLeast(1)))
                        onProgress(emit)
                        lastEmitPct = emit
                        lastEmitTime = now
                    }
                }
                output.flush()
            }

            if (outFile.exists()) outFile.delete()
            if (!tmp.renameTo(outFile)) tmp.copyTo(outFile, overwrite = true).also { tmp.delete() }

            onProgress(100)
        }
    }

}
