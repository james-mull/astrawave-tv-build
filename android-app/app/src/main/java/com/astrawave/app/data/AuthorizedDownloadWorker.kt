package com.astrawave.app.data

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.astrawave.app.core.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Executes user-authorized direct HTTP(S) downloads into app-private storage.
 * AstraWave never discovers a downloadable URL here; it only transports URLs already approved
 * by the source resolver/provider eligibility layer.
 */
class AuthorizedDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_DOWNLOAD_ID).orEmpty()
        val profileId = inputData.getString(KEY_PROFILE_ID).orEmpty()
        if (id.isBlank() || profileId.isBlank()) return@withContext Result.failure()

        val store = DownloadTravelStore(applicationContext)
        val item = store.find(id) ?: return@withContext Result.failure()
        if (item.state == DownloadState.CANCELED || item.state == DownloadState.COMPLETE) {
            return@withContext Result.success()
        }

        val policy = store.travelPolicy(profileId)
        val root = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(applicationContext.filesDir, "downloads")
        val directory = File(root, "astrawave/${safeSegment(profileId)}").apply { mkdirs() }
        val extension = extensionFor(item.sourceUrl)
        val target = File(directory, "${safeSegment(id)}$extension")
        val partial = File(directory, "${safeSegment(id)}$extension.part")

        var connection: HttpURLConnection? = null
        try {
            store.update(id, DownloadState.DOWNLOADING, item.progressPercent.coerceIn(0, 99), error = null)
            connection = (URL(item.sourceUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "*/*")
                connect()
            }
            val code = connection.responseCode
            if (code !in 200..299) throw IOException("Download server returned HTTP $code")

            val expectedBytes = connection.contentLengthLong.takeIf { it > 0L }
            val storageCapBytes = policy.maxStorageGb.coerceAtLeast(1).toLong() * 1024L * 1024L * 1024L
            val usedBytes = directory.listFiles()?.filter { it.isFile && it != partial }?.sumOf { it.length() } ?: 0L
            if (expectedBytes != null && usedBytes + expectedBytes > storageCapBytes) {
                throw IOException("Travel Mode storage cap would be exceeded")
            }

            if (partial.exists()) partial.delete()
            var copied = 0L
            var lastProgress = -1
            connection.inputStream.buffered(128 * 1024).use { input ->
                partial.outputStream().buffered(128 * 1024).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        if (isStopped) throw IOException("Download canceled")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (copied + usedBytes > storageCapBytes) {
                            throw IOException("Travel Mode storage cap exceeded")
                        }
                        val progress = expectedBytes?.let { ((copied * 100L) / it).toInt().coerceIn(0, 99) } ?: 0
                        if (progress != lastProgress) {
                            lastProgress = progress
                            store.update(id, DownloadState.DOWNLOADING, progress)
                            setProgress(androidx.work.workDataOf("progress" to progress))
                        }
                    }
                }
            }

            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) {
                partial.copyTo(target, overwrite = true)
                partial.delete()
            }
            store.update(id, DownloadState.COMPLETE, 100, localUri = target.toURI().toString(), error = null)
            Result.success()
        } catch (error: Exception) {
            partial.delete()
            if (isStopped) {
                store.update(id, DownloadState.CANCELED, item.progressPercent, error = "Canceled")
                Result.success()
            } else if (runAttemptCount < 2 && error is IOException) {
                store.update(id, DownloadState.QUEUED, item.progressPercent, error = error.message)
                Result.retry()
            } else {
                store.update(id, DownloadState.FAILED, item.progressPercent, error = error.message ?: "Download failed")
                Result.failure()
            }
        } finally {
            connection?.disconnect()
        }
    }

    private fun safeSegment(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)

    private fun extensionFor(url: String): String {
        val path = runCatching { URL(url).path }.getOrDefault("")
        val ext = path.substringAfterLast('/', "").substringAfterLast('.', "")
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
        return ext?.let { ".$it" } ?: ".media"
    }

    companion object {
        const val KEY_DOWNLOAD_ID = "downloadId"
        const val KEY_PROFILE_ID = "profileId"
    }
}
