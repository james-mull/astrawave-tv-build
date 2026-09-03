package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.CatchUpItem
import com.astrawave.app.core.DvrEligibility
import com.astrawave.app.core.DvrGateway
import com.astrawave.app.core.LiveSourceCapabilities
import com.astrawave.app.core.Recording
import com.astrawave.app.core.RecordingRequest
import com.astrawave.app.core.RecordingState
import com.astrawave.app.core.TimeshiftSession
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local persistence/orchestration layer for DVR requests. It never manufactures DVR capability:
 * each source must explicitly register support before scheduling, catch-up, or timeshift is exposed.
 * Actual recording transport/storage is supplied by a provider-specific adapter later.
 */
class LocalDvrGateway(context: Context) : DvrGateway {
    private val prefs = context.getSharedPreferences("astrawave_dvr_v1", Context.MODE_PRIVATE)

    fun registerCapabilities(capabilities: LiveSourceCapabilities) {
        val all = loadCapabilities().toMutableMap()
        all[capabilities.sourceId] = capabilities
        val root = JSONObject()
        all.values.forEach { cap ->
            root.put(cap.sourceId, JSONObject()
                .put("supportsDvr", cap.supportsDvr)
                .put("supportsCatchUp", cap.supportsCatchUp)
                .put("supportsTimeshift", cap.supportsTimeshift)
                .put("catchUpWindowHours", cap.catchUpWindowHours ?: JSONObject.NULL)
                .put("maxRecordingHours", cap.maxRecordingHours ?: JSONObject.NULL))
        }
        prefs.edit().putString(KEY_CAPABILITIES, root.toString()).apply()
    }

    override fun capabilities(sourceId: String): LiveSourceCapabilities =
        loadCapabilities()[sourceId] ?: LiveSourceCapabilities(sourceId = sourceId)

    override fun schedule(request: RecordingRequest): Recording {
        require(request.endEpochMs > request.startEpochMs) { "Recording end time must be after start time" }
        val cap = capabilities(request.sourceId)
        require(DvrEligibility.canSchedule(cap)) { "This source does not advertise authorized DVR support" }
        cap.maxRecordingHours?.let { maxHours ->
            val durationMs = request.endEpochMs - request.startEpochMs
            require(durationMs <= maxHours * 3_600_000L) { "Recording exceeds this source's maximum duration" }
        }
        val recording = Recording(request = request, state = RecordingState.SCHEDULED)
        upsert(recording)
        return recording
    }

    override fun cancel(recordingId: String): Boolean {
        val all = loadRecordings().toMutableList()
        val index = all.indexOfFirst { it.request.id == recordingId }
        if (index < 0) return false
        all[index] = all[index].copy(state = RecordingState.CANCELED)
        writeRecordings(all)
        return true
    }

    override fun recordings(profileId: String): List<Recording> =
        loadRecordings().filter { it.request.profileId == profileId }.sortedByDescending { it.request.startEpochMs }

    override fun catchUp(channelId: String, fromEpochMs: Long, toEpochMs: Long): List<CatchUpItem> = emptyList()

    override fun startTimeshift(sourceId: String, channelId: String): TimeshiftSession? {
        val cap = capabilities(sourceId)
        if (!DvrEligibility.canTimeshift(cap)) return null
        val now = System.currentTimeMillis()
        val windowMs = (cap.catchUpWindowHours ?: 2).coerceAtLeast(1) * 3_600_000L
        return TimeshiftSession(
            id = "timeshift:$sourceId:$channelId:$now",
            sourceId = sourceId,
            channelId = channelId,
            liveEdgeEpochMs = now,
            earliestSeekEpochMs = now - windowMs,
            currentPositionEpochMs = now,
        )
    }

    private fun upsert(recording: Recording) {
        val all = loadRecordings().toMutableList()
        val index = all.indexOfFirst { it.request.id == recording.request.id }
        if (index >= 0) all[index] = recording else all += recording
        writeRecordings(all)
    }

    private fun loadCapabilities(): Map<String, LiveSourceCapabilities> {
        val raw = prefs.getString(KEY_CAPABILITIES, null) ?: return emptyMap()
        return runCatching {
            val root = JSONObject(raw)
            buildMap {
                root.keys().forEach { sourceId ->
                    val obj = root.getJSONObject(sourceId)
                    put(sourceId, LiveSourceCapabilities(
                        sourceId = sourceId,
                        supportsDvr = obj.optBoolean("supportsDvr"),
                        supportsCatchUp = obj.optBoolean("supportsCatchUp"),
                        supportsTimeshift = obj.optBoolean("supportsTimeshift"),
                        catchUpWindowHours = obj.optInt("catchUpWindowHours").takeIf { !obj.isNull("catchUpWindowHours") },
                        maxRecordingHours = obj.optInt("maxRecordingHours").takeIf { !obj.isNull("maxRecordingHours") },
                    ))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun loadRecordings(): List<Recording> {
        val raw = prefs.getString(KEY_RECORDINGS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val req = obj.getJSONObject("request")
                    add(Recording(
                        request = RecordingRequest(
                            id = req.getString("id"),
                            profileId = req.getString("profileId"),
                            sourceId = req.getString("sourceId"),
                            channelId = req.getString("channelId"),
                            title = req.getString("title"),
                            startEpochMs = req.getLong("startEpochMs"),
                            endEpochMs = req.getLong("endEpochMs"),
                            seriesId = req.optString("seriesId").takeIf { it.isNotBlank() },
                            eventId = req.optString("eventId").takeIf { it.isNotBlank() },
                        ),
                        state = runCatching { RecordingState.valueOf(obj.getString("state")) }.getOrDefault(RecordingState.FAILED),
                        playbackUrl = obj.optString("playbackUrl").takeIf { it.isNotBlank() },
                        error = obj.optString("error").takeIf { it.isNotBlank() },
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeRecordings(recordings: List<Recording>) {
        val array = JSONArray()
        recordings.forEach { recording ->
            val req = recording.request
            array.put(JSONObject()
                .put("state", recording.state.name)
                .put("playbackUrl", recording.playbackUrl ?: JSONObject.NULL)
                .put("error", recording.error ?: JSONObject.NULL)
                .put("request", JSONObject()
                    .put("id", req.id)
                    .put("profileId", req.profileId)
                    .put("sourceId", req.sourceId)
                    .put("channelId", req.channelId)
                    .put("title", req.title)
                    .put("startEpochMs", req.startEpochMs)
                    .put("endEpochMs", req.endEpochMs)
                    .put("seriesId", req.seriesId ?: JSONObject.NULL)
                    .put("eventId", req.eventId ?: JSONObject.NULL)))
        }
        prefs.edit().putString(KEY_RECORDINGS, array.toString()).apply()
    }

    private companion object {
        const val KEY_CAPABILITIES = "capabilities"
        const val KEY_RECORDINGS = "recordings"
    }
}
