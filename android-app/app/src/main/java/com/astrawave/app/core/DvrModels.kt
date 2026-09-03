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
}
