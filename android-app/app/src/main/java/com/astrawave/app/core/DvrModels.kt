package com.astrawave.app.core

/** Capability-gated DVR, catch-up and timeshift models for supported authorized sources. */
data class LiveSourceCapabilities(
    val sourceId: String,
    val supportsDvr: Boolean = false,
    val supportsCatchUp: Boolean = false,
    val supportsTimeshift: Boolean = false,
    val catchUpWindowHours: Int? = null,
    val maxRecordingHours: Int? = null,
)

enum class RecordingState {
    SCHEDULED,
    RECORDING,
    COMPLETE,
    FAILED,
    CANCELED,
}

data class RecordingRequest(
    val id: String,
    val profileId: String,
    val sourceId: String,
    val channelId: String,
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val seriesId: String? = null,
    val eventId: String? = null,
)

data class Recording(
    val request: RecordingRequest,
    val state: RecordingState,
    val playbackUrl: String? = null,
    val error: String? = null,
)

data class TimeshiftSession(
    val id: String,
    val sourceId: String,
    val channelId: String,
    val liveEdgeEpochMs: Long,
    val earliestSeekEpochMs: Long,
    val currentPositionEpochMs: Long,
)

data class CatchUpItem(
    val sourceId: String,
    val channelId: String,
    val programId: String,
    val title: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val playbackUrl: String,
)

interface DvrGateway {
    fun capabilities(sourceId: String): LiveSourceCapabilities
    fun schedule(request: RecordingRequest): Recording
    fun cancel(recordingId: String): Boolean
    fun recordings(profileId: String): List<Recording>
    fun catchUp(channelId: String, fromEpochMs: Long, toEpochMs: Long): List<CatchUpItem>
    fun startTimeshift(sourceId: String, channelId: String): TimeshiftSession?
}

object DvrEligibility {
    fun canSchedule(capabilities: LiveSourceCapabilities): Boolean = capabilities.supportsDvr
    fun canCatchUp(capabilities: LiveSourceCapabilities): Boolean = capabilities.supportsCatchUp
    fun canTimeshift(capabilities: LiveSourceCapabilities): Boolean = capabilities.supportsTimeshift

    fun validateRequest(
        request: RecordingRequest,
        capabilities: LiveSourceCapabilities,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<String> = buildList {
        if (request.id.isBlank()) add("Recording id is required")
        if (request.profileId.isBlank()) add("Profile id is required")
        if (request.sourceId.isBlank()) add("Source id is required")
        if (request.channelId.isBlank()) add("Channel id is required")
        if (request.title.isBlank()) add("Recording title is required")
        if (request.endEpochMs <= request.startEpochMs) add("Recording end time must be after start time")
        if (request.endEpochMs <= nowEpochMs) add("Recording has already ended")
        if (!canSchedule(capabilities)) add("This source does not advertise authorized DVR support")
        capabilities.maxRecordingHours?.let { maxHours ->
            val durationMs = request.endEpochMs - request.startEpochMs
            if (durationMs > maxHours * 3_600_000L) add("Recording exceeds this source's maximum duration")
        }
    }
}
