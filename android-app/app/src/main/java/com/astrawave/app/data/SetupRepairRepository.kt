package com.astrawave.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SetupRepairReport(
    val sourcesChecked: Int,
    val sourcesHealthy: Int,
    val duplicatesRemoved: Int,
    val travelNormalized: Boolean,
    val messages: List<String>,
)

/**
 * Safe, non-destructive setup repair pass.
 *
 * This never changes provider passwords, debrid/personal-media credentials, DVR authorization,
 * parental controls, subscription state, or deletes user libraries. It only normalizes local
 * source metadata, re-tests enabled customer IPTV sources, refreshes their channel/EPG counts,
 * and bounds Travel Mode values to supported ranges.
 */
class SetupRepairRepository(private val context: Context) {
    suspend fun repair(profileId: String): SetupRepairReport = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val sourceStore = IptvSourceStore(app)
        val sourceRepository = IptvSourceRepository()
        val original = sourceStore.load(profileId)

        val deduped = original
            .groupBy { it.id }
            .map { (_, matches) -> matches.last() }
            .map { source -> source.copy(priority = source.priority.coerceIn(1, 100)) }
        val duplicatesRemoved = (original.size - deduped.size).coerceAtLeast(0)

        var checked = 0
        var healthy = 0
        val refreshed = deduped.map { source ->
            if (!source.enabled) return@map source
            checked += 1
            val result = sourceRepository.test(source)
            if (result.status == com.astrawave.app.core.IptvSourceStatus.READY) healthy += 1
            source.copy(
                status = result.status,
                channelCount = result.channelCount,
                guideProgramCount = result.guideProgramCount,
                lastCheckedEpochMs = System.currentTimeMillis(),
                lastError = result.error,
            )
        }
        sourceStore.save(profileId, refreshed)

        val downloads = DownloadTravelStore(app)
        val currentPolicy = downloads.travelPolicy(profileId)
        val normalizedPolicy = currentPolicy.copy(
            maxStorageGb = currentPolicy.maxStorageGb.coerceIn(1, 500),
            movieTarget = currentPolicy.movieTarget.coerceIn(0, 100),
            episodeTarget = currentPolicy.episodeTarget.coerceIn(0, 500),
            podcastTarget = currentPolicy.podcastTarget.coerceIn(0, 500),
        )
        val travelNormalized = normalizedPolicy != currentPolicy
        if (travelNormalized) downloads.saveTravelPolicy(profileId, normalizedPolicy)

        val messages = buildList {
            if (duplicatesRemoved > 0) add("Removed $duplicatesRemoved duplicate source record${if (duplicatesRemoved == 1) "" else "s"}.")
            if (checked > 0) add("Re-tested $checked enabled customer source${if (checked == 1) "" else "s"}; $healthy healthy.")
            if (travelNormalized) add("Normalized Travel Mode limits to supported ranges.")
            if (isEmpty()) add("No safe repairs were needed. Your local setup is already normalized.")
        }

        SetupRepairReport(
            sourcesChecked = checked,
            sourcesHealthy = healthy,
            duplicatesRemoved = duplicatesRemoved,
            travelNormalized = travelNormalized,
            messages = messages,
        )
    }
}
