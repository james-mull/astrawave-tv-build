package com.astrawave.app.data

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.astrawave.app.core.Recording
import com.astrawave.app.core.RecordingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Executes DVR capture only from stream URLs already attached to an authorized RecordingRequest.
 * The first transport intentionally supports continuous direct HTTP(S) streams. HLS manifests are
 * rejected rather than being incorrectly saved as playlist text; a future HLS segment recorder can
 * add that transport explicitly.
 */
class DvrRecordingWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val recordingId = inputData.getString(KEY_RECORDING_ID).orEmpty()
        if (recordingId.isBlank()) return@withContext Result.failure()

        val gateway = LocalDvrGateway(applicationContext)
        val recording = gateway.find(recordingId) ?: return@withContext Result.failure()
        if (recording.state == RecordingState.CANCELED || recording.state == RecordingState.COMPLETE) {
            return@withContext Result.success()
        }
        val request = recording.request
        val now = System.currentTimeMillis()
        if (request.endEpochMs <= now) {
            gateway.updateRecording(recordingId, RecordingState.FAILED, error = "Recording window already ended")
            return@withContext Result.failure()
        }

        val urls = request.authorizedStreamUrls
            .map(String::trim)
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
        if (urls.isEmpty()) {
            gateway.updateRecording(recordingId, RecordingState.FAILED, error = "No authorized recording stream is available")
            return@withContext Result.failure()
        }

        val root = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(applicationContext.filesDir, "recordings")
        val directory = File(root, "astrawave-dvr/${safe(request.profileId)}").apply { mkdirs() }
        val target = File(directory, "${safe(recordingId)}.ts")
        val partial = File(directory, "${safe(recordingId)}.ts.part")
        partial.delete()

        gateway.updateRecording(recordingId, RecordingState.RECORDING, error = null)
        var lastError: String? = null
        for (streamUrl in urls) {
            if (isStopped || gateway.find(recordingId)?.state == RecordingState.CANCELED) {
                partial.delete()
                return@withContext Result.success()
            }
            if (looksLikeHls(streamUrl)) {
                lastError = "HLS DVR transport is not enabled for this source yet"
                continue
            }
            val outcome = runCatching { captureDirect(streamUrl, partial, request.endEpochMs, gateway, recordingId) }
            if (outcome.isSuccess) {
                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
                gateway.updateRecording(
                    recordingId,
                    RecordingState.COMPLETE,
                    playbackUrl = target.toURI().toString(),
                    error = null,
                )
                return@withContext Result.success()
            }
            lastError = outcome.exceptionOrNull()?.message ?: "Recording source failed"
            partial.delete()
        }

        gateway.updateRecording(recordingId, RecordingState.FAILED, error = lastError ?: "All authorized recording sources failed")
        Result.failure()
    }

    private fun captureDirect(
        streamUrl: String,
        partial: File,
        endEpochMs: Long,
        gateway: LocalDvrGateway,
        recordingId: String,
    ) {
        val connection = URL(streamUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "video/mp2t,video/*,*/*")
            connection.setRequestProperty("User-Agent", "AstraWave-DVR/1.0")
            connection.connect()
            if (connection.responseCode !in 200..299) throw IOException("DVR source returned HTTP ${connection.responseCode}")
            val contentType = connection.contentType.orEmpty().lowercase()
            if (contentType.contains("mpegurl") || contentType.contains("m3u8")) {
                throw IOException("HLS DVR transport is not enabled for this source yet")
            }

            connection.inputStream.buffered(128 * 1024).use { input ->
                partial.outputStream().buffered(128 * 1024).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    while (System.currentTimeMillis() < endEpochMs) {
                        if (isStopped || gateway.find(recordingId)?.state == RecordingState.CANCELED) {
                            throw RecordingCanceled()
                        }
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read > 0) output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }
            if (!partial.exists() || partial.length() <= 0L) throw IOException("Recording source returned no media bytes")
        } catch (cancel: RecordingCanceled) {
            throw cancel
        } finally {
            connection.disconnect()
        }
    }

    private fun looksLikeHls(url: String): Boolean =
        runCatching { URL(url).path.lowercase().endsWith(".m3u8") }.getOrDefault(false)

    private fun safe(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96)

    private class RecordingCanceled : IOException("Recording canceled")

    companion object {
        const val KEY_RECORDING_ID = "recordingId"
    }
}

object DvrRecordingScheduler {
    private const val PREFIX = "astrawave-dvr:"

    fun schedule(context: Context, recording: Recording) {
        val delay = (recording.request.startEpochMs - System.currentTimeMillis()).coerceAtLeast(0L)
        val input = Data.Builder().putString(DvrRecordingWorker.KEY_RECORDING_ID, recording.request.id).build()
        val request = OneTimeWorkRequestBuilder<DvrRecordingWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(recording.request.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(context: Context, recordingId: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(recordingId))
    }

    private fun workName(recordingId: String) = "$PREFIX${recordingId.replace(':', '_')}"
}
