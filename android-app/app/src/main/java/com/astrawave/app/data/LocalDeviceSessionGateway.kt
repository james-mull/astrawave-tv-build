package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.AstraWaveDevice
import com.astrawave.app.core.AstraWaveDeviceType
import com.astrawave.app.core.DeviceSessionGateway
import com.astrawave.app.core.DeviceSessionState
import com.astrawave.app.core.PairingSession
import com.astrawave.app.core.PlaybackHandoff
import com.astrawave.app.core.RemoteCommandEnvelope
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Local persistence and pairing foundation for AstraWave companion/device workflows.
 * This does not pretend to be Google Cast or a LAN transport. Platform transports
 * plug into this layer later while pairing, device state and handoff remain normalized.
 */
class LocalDeviceSessionGateway(context: Context) : DeviceSessionGateway {
    private val prefs = context.getSharedPreferences("astrawave_device_sessions_v1", Context.MODE_PRIVATE)

    override fun discover(): List<AstraWaveDevice> = loadDevices()

    override fun createPairingSession(targetDeviceId: String?): PairingSession {
        val sessionId = UUID.randomUUID().toString()
        val expires = System.currentTimeMillis() + PAIRING_TTL_MS
        val payload = JSONObject()
            .put("scheme", "astrawave-pair")
            .put("sessionId", sessionId)
            .put("targetDeviceId", targetDeviceId ?: JSONObject.NULL)
            .put("expiresAt", expires)
            .toString()
        return PairingSession(sessionId, payload, expires, targetDeviceId)
    }

    override fun pair(qrPayload: String): AstraWaveDevice {
        val root = JSONObject(qrPayload)
        require(root.optString("scheme") == "astrawave-pair") { "Invalid AstraWave pairing payload" }
        val expiresAt = root.optLong("expiresAt")
        require(expiresAt > System.currentTimeMillis()) { "Pairing session has expired" }
        val id = root.optString("targetDeviceId").takeIf { it.isNotBlank() && it != "null" }
            ?: UUID.randomUUID().toString()
        val existing = loadDevices().firstOrNull { it.id == id }
        val device = (existing ?: AstraWaveDevice(
            id = id,
            name = "Paired AstraWave Device",
            type = AstraWaveDeviceType.PHONE,
        )).copy(state = DeviceSessionState.CONNECTED)
        saveDevice(device)
        return device
    }

    override fun sendRemoteCommand(command: RemoteCommandEnvelope): Boolean {
        val connected = loadDevices().any { it.id == command.deviceId && it.state == DeviceSessionState.CONNECTED }
        if (!connected) return false
        prefs.edit().putString(KEY_LAST_REMOTE, encodeRemote(command).toString()).apply()
        return true
    }

    override fun handoff(playback: PlaybackHandoff): Boolean {
        val target = loadDevices().firstOrNull { it.id == playback.targetDeviceId } ?: return false
        if (!target.supportsHandoff || target.state != DeviceSessionState.CONNECTED) return false
        prefs.edit().putString(KEY_PENDING_HANDOFF, encodeHandoff(playback).toString()).apply()
        return true
    }

    fun pendingHandoff(): PlaybackHandoff? = prefs.getString(KEY_PENDING_HANDOFF, null)?.let { raw ->
        runCatching { decodeHandoff(JSONObject(raw)) }.getOrNull()
    }

    fun clearPendingHandoff() {
        prefs.edit().remove(KEY_PENDING_HANDOFF).apply()
    }

    fun removeDevice(deviceId: String) {
        writeDevices(loadDevices().filterNot { it.id == deviceId })
    }

    fun renameDevice(deviceId: String, name: String) {
        writeDevices(loadDevices().map { if (it.id == deviceId) it.copy(name = name.trim().ifBlank { it.name }) else it })
    }

    private fun saveDevice(device: AstraWaveDevice) {
        val devices = loadDevices().filterNot { it.id == device.id } + device
        writeDevices(devices)
    }

    private fun loadDevices(): List<AstraWaveDevice> {
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) array.optJSONObject(i)?.let(::decodeDevice)?.let(::add)
            }
        }.getOrDefault(emptyList())
    }

    private fun writeDevices(devices: List<AstraWaveDevice>) {
        val array = JSONArray()
        devices.forEach { array.put(encodeDevice(it)) }
        prefs.edit().putString(KEY_DEVICES, array.toString()).apply()
    }

    private fun encodeDevice(device: AstraWaveDevice) = JSONObject()
        .put("id", device.id)
        .put("name", device.name)
        .put("type", device.type.name)
        .put("state", device.state.name)
        .put("supportsRemote", device.supportsRemote)
        .put("supportsHandoff", device.supportsHandoff)
        .put("supportsCasting", device.supportsCasting)

    private fun decodeDevice(obj: JSONObject): AstraWaveDevice? = runCatching {
        AstraWaveDevice(
            id = obj.getString("id"),
            name = obj.optString("name").ifBlank { "AstraWave Device" },
            type = AstraWaveDeviceType.valueOf(obj.optString("type", AstraWaveDeviceType.PHONE.name)),
            state = DeviceSessionState.valueOf(obj.optString("state", DeviceSessionState.DISCONNECTED.name)),
            supportsRemote = obj.optBoolean("supportsRemote", true),
            supportsHandoff = obj.optBoolean("supportsHandoff", true),
            supportsCasting = obj.optBoolean("supportsCasting", false),
        )
    }.getOrNull()

    private fun encodeRemote(command: RemoteCommandEnvelope) = JSONObject()
        .put("sessionId", command.sessionId)
        .put("deviceId", command.deviceId)
        .put("command", command.command.name)
        .put("value", command.value ?: JSONObject.NULL)
        .put("sentAt", command.sentAtEpochMs)

    private fun encodeHandoff(playback: PlaybackHandoff) = JSONObject()
        .put("mediaId", playback.mediaId)
        .put("mediaType", playback.mediaType)
        .put("title", playback.title)
        .put("streamUrl", playback.streamUrl ?: JSONObject.NULL)
        .put("positionMs", playback.positionMs)
        .put("durationMs", playback.durationMs)
        .put("sourceDeviceId", playback.sourceDeviceId)
        .put("targetDeviceId", playback.targetDeviceId)
        .put("createdAt", playback.createdAtEpochMs)

    private fun decodeHandoff(obj: JSONObject) = PlaybackHandoff(
        mediaId = obj.getString("mediaId"),
        mediaType = obj.getString("mediaType"),
        title = obj.optString("title"),
        streamUrl = if (obj.isNull("streamUrl")) null else obj.optString("streamUrl").takeIf { it.isNotBlank() },
        positionMs = obj.optLong("positionMs"),
        durationMs = obj.optLong("durationMs"),
        sourceDeviceId = obj.getString("sourceDeviceId"),
        targetDeviceId = obj.getString("targetDeviceId"),
        createdAtEpochMs = obj.optLong("createdAt"),
    )

    private companion object {
        const val KEY_DEVICES = "paired_devices"
        const val KEY_LAST_REMOTE = "last_remote_command"
        const val KEY_PENDING_HANDOFF = "pending_handoff"
        const val PAIRING_TTL_MS = 5 * 60 * 1000L
    }
}
