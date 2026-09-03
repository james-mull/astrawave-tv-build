package com.astrawave.app.core

/** Shared model for casting, QR pairing, phone remote control and device handoff. */
enum class AstraWaveDeviceType {
    PHONE,
    TABLET,
    ANDROID_TV,
    FIRE_TV,
    WEB,
    CAST_RECEIVER,
}

enum class DeviceSessionState {
    DISCOVERED,
    PAIRING,
    CONNECTED,
    DISCONNECTED,
    ERROR,
}

data class AstraWaveDevice(
    val id: String,
    val name: String,
    val type: AstraWaveDeviceType,
    val state: DeviceSessionState = DeviceSessionState.DISCOVERED,
    val supportsRemote: Boolean = true,
    val supportsHandoff: Boolean = true,
    val supportsCasting: Boolean = false,
)

data class PairingSession(
    val sessionId: String,
    val qrPayload: String,
    val expiresAtEpochMs: Long,
    val targetDeviceId: String? = null,
)

data class PlaybackHandoff(
    val mediaId: String,
    val mediaType: String,
    val title: String,
    val streamUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val createdAtEpochMs: Long,
)

enum class RemoteCommand {
    PLAY,
    PAUSE,
    SEEK_FORWARD,
    SEEK_BACK,
    CHANNEL_UP,
    CHANNEL_DOWN,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    BACK,
    HOME,
}

data class RemoteCommandEnvelope(
    val sessionId: String,
    val deviceId: String,
    val command: RemoteCommand,
    val value: Long? = null,
    val sentAtEpochMs: Long,
)

object DeviceSessionValidation {
    private const val HANDOFF_MAX_AGE_MS = 10 * 60 * 1000L

    fun pairingValid(session: PairingSession, nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        session.sessionId.isNotBlank() &&
            session.qrPayload.isNotBlank() &&
            session.expiresAtEpochMs > nowEpochMs

    fun handoffValid(handoff: PlaybackHandoff, nowEpochMs: Long = System.currentTimeMillis()): Boolean =
        handoff.mediaId.isNotBlank() &&
            handoff.mediaType.isNotBlank() &&
            handoff.sourceDeviceId.isNotBlank() &&
            handoff.targetDeviceId.isNotBlank() &&
            handoff.sourceDeviceId != handoff.targetDeviceId &&
            handoff.positionMs >= 0L &&
            handoff.durationMs >= 0L &&
            (handoff.durationMs == 0L || handoff.positionMs <= handoff.durationMs) &&
            handoff.createdAtEpochMs in (nowEpochMs - HANDOFF_MAX_AGE_MS)..nowEpochMs
}

interface DeviceSessionGateway {
    fun discover(): List<AstraWaveDevice>
    fun createPairingSession(targetDeviceId: String? = null): PairingSession
    fun pair(qrPayload: String): AstraWaveDevice
    fun sendRemoteCommand(command: RemoteCommandEnvelope): Boolean
    fun handoff(playback: PlaybackHandoff): Boolean
}
