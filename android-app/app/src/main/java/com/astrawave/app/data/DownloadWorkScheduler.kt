package com.astrawave.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.astrawave.app.core.DownloadRequest

/** Schedules authorized offline downloads with durable network constraints. */
object DownloadWorkScheduler {
    private fun workName(id: String) = "astrawave-download-$id"

    fun enqueue(context: Context, request: DownloadRequest) {
        val policy = DownloadTravelStore(context.applicationContext).travelPolicy(request.profileId)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (policy.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val work = OneTimeWorkRequestBuilder<AuthorizedDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(
                AuthorizedDownloadWorker.KEY_DOWNLOAD_ID to request.id,
                AuthorizedDownloadWorker.KEY_PROFILE_ID to request.profileId,
            ))
            .addTag(workName(request.id))
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(workName(request.id), ExistingWorkPolicy.REPLACE, work)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(id))
    }
}
