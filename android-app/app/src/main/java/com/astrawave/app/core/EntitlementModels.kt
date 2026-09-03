package com.astrawave.app.core

/** AstraWave subscription and feature-access contract. */
enum class AstraWavePlan {
    FREE,
    PLUS,
}

enum class AstraWaveEntitlement {
    CLOUD_SYNC,
    MULTIVIEW,
    DEVICE_HANDOFF,
    DVR,
    ADVANCED_RECOMMENDATIONS,
    PREMIUM_THEMES,
    EXTRA_PROFILES,
}

data class EntitlementSnapshot(
    val userId: String?,
    val plan: AstraWavePlan = AstraWavePlan.FREE,
    val activeEntitlements: Set<AstraWaveEntitlement> = emptySet(),
    val trialEndsAtEpochMs: Long? = null,
    val renewsAtEpochMs: Long? = null,
    val source: String = "local",
) {
    fun has(entitlement: AstraWaveEntitlement): Boolean = entitlement in activeEntitlements
}

object AstraWaveEntitlementPolicy {
    val freeDefaults: Set<AstraWaveEntitlement> = emptySet()

    val plusDefaults: Set<AstraWaveEntitlement> = setOf(
        AstraWaveEntitlement.CLOUD_SYNC,
        AstraWaveEntitlement.MULTIVIEW,
        AstraWaveEntitlement.DEVICE_HANDOFF,
        AstraWaveEntitlement.DVR,
        AstraWaveEntitlement.ADVANCED_RECOMMENDATIONS,
        AstraWaveEntitlement.PREMIUM_THEMES,
        AstraWaveEntitlement.EXTRA_PROFILES,
    )

    fun snapshot(userId: String?, plan: AstraWavePlan): EntitlementSnapshot =
        EntitlementSnapshot(
            userId = userId,
            plan = plan,
            activeEntitlements = if (plan == AstraWavePlan.PLUS) plusDefaults else freeDefaults,
        )
}
