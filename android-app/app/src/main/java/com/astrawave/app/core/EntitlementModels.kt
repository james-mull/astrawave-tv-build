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
    fun premiumActive(atEpochMs: Long = System.currentTimeMillis()): Boolean {
        if (plan != AstraWavePlan.PLUS) return false
        val trialEnd = trialEndsAtEpochMs
        if (trialEnd != null && renewsAtEpochMs == null && atEpochMs >= trialEnd) return false
        return true
    }

    fun effectiveEntitlements(atEpochMs: Long = System.currentTimeMillis()): Set<AstraWaveEntitlement> =
        if (premiumActive(atEpochMs)) activeEntitlements else AstraWaveEntitlementPolicy.freeDefaults

    fun has(entitlement: AstraWaveEntitlement, atEpochMs: Long = System.currentTimeMillis()): Boolean =
        entitlement in effectiveEntitlements(atEpochMs)
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

    fun snapshot(
        userId: String?,
        plan: AstraWavePlan,
        trialEndsAtEpochMs: Long? = null,
        renewsAtEpochMs: Long? = null,
        source: String = "local",
    ): EntitlementSnapshot = EntitlementSnapshot(
        userId = userId,
        plan = plan,
        activeEntitlements = if (plan == AstraWavePlan.PLUS) plusDefaults else freeDefaults,
        trialEndsAtEpochMs = trialEndsAtEpochMs,
        renewsAtEpochMs = renewsAtEpochMs,
        source = source,
    )
}
