package com.astrawave.app.core

/** AstraWave subscription and feature-access contract. */
enum class AstraWavePlan(
    val displayName: String,
    val monthlyPriceUsd: Double?,
) {
    FREE("AstraWave Free", null),
    PLUS("AstraWave Plus", 9.99),
    PREMIUM("AstraWave Premium", 19.99),
}

enum class AstraWaveEntitlement {
    CLOUD_SYNC,
    MULTIVIEW,
    DEVICE_HANDOFF,
    DVR,
    ADVANCED_RECOMMENDATIONS,
    PREMIUM_THEMES,
    EXTRA_PROFILES,
    PRIORITY_SOURCE_FAILOVER,
    PREMIUM_SPORTS_HUB,
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
        if (plan == AstraWavePlan.FREE) return false
        val trialEnd = trialEndsAtEpochMs
        if (trialEnd != null && renewsAtEpochMs == null && atEpochMs >= trialEnd) return false
        return true
    }

    fun trialActive(atEpochMs: Long = System.currentTimeMillis()): Boolean =
        trialEndsAtEpochMs?.let { atEpochMs < it } == true

    fun daysRemainingInTrial(atEpochMs: Long = System.currentTimeMillis()): Int? {
        val end = trialEndsAtEpochMs ?: return null
        if (end <= atEpochMs) return 0
        val day = 24L * 60L * 60L * 1000L
        return ((end - atEpochMs + day - 1) / day).toInt()
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
        AstraWaveEntitlement.DEVICE_HANDOFF,
        AstraWaveEntitlement.PREMIUM_THEMES,
        AstraWaveEntitlement.EXTRA_PROFILES,
    )

    val premiumDefaults: Set<AstraWaveEntitlement> = plusDefaults + setOf(
        AstraWaveEntitlement.MULTIVIEW,
        AstraWaveEntitlement.DVR,
        AstraWaveEntitlement.ADVANCED_RECOMMENDATIONS,
        AstraWaveEntitlement.PRIORITY_SOURCE_FAILOVER,
        AstraWaveEntitlement.PREMIUM_SPORTS_HUB,
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
        activeEntitlements = when (plan) {
            AstraWavePlan.FREE -> freeDefaults
            AstraWavePlan.PLUS -> plusDefaults
            AstraWavePlan.PREMIUM -> premiumDefaults
        },
        trialEndsAtEpochMs = trialEndsAtEpochMs,
        renewsAtEpochMs = renewsAtEpochMs,
        source = source,
    )
}
