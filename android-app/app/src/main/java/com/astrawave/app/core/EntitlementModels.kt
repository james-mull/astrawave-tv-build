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
    WEB_CONTROL_CENTER,
    LIVE_DEVICE_SYNC,
    REMOTE_CONTROL,
    DEVICE_HANDOFF,
    MULTIVIEW,
    SIX_PANE_MULTIVIEW,
    DVR,
    SERIES_RECORDING,
    CATCH_UP,
    TIMESHIFT,
    DOWNLOADS,
    TRAVEL_MODE,
    ADVANCED_RECOMMENDATIONS,
    ASTRA_CONCIERGE,
    PREMIUM_THEMES,
    EXTRA_PROFILES,
    HOUSEHOLD_VOTING,
    PRIORITY_SOURCE_FAILOVER,
    SOURCE_HEALTH_HISTORY,
    ADVANCED_EPG,
    CHANNEL_EDITOR,
    UNLIMITED_PROVIDERS,
    PREMIUM_SPORTS_HUB,
    SPORTS_REMINDERS,
    PERSONAL_MEDIA_AGGREGATION,
    AUDIO_PREMIUM,
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
        val paidThrough = renewsAtEpochMs
        if (trialEnd != null && paidThrough == null && atEpochMs >= trialEnd) return false
        // Server-verified recurring subscriptions fail closed after their paid-through timestamp.
        // A fresh Play verification (including grace-period access) advances this timestamp.
        if (paidThrough != null && atEpochMs >= paidThrough) return false
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
        AstraWaveEntitlement.WEB_CONTROL_CENTER,
        AstraWaveEntitlement.DEVICE_HANDOFF,
        AstraWaveEntitlement.MULTIVIEW,
        AstraWaveEntitlement.DVR,
        AstraWaveEntitlement.ADVANCED_RECOMMENDATIONS,
        AstraWaveEntitlement.PREMIUM_THEMES,
        AstraWaveEntitlement.EXTRA_PROFILES,
    )

    /** $19.99 plan: recurring intelligence, reliability, household and power-TV value. */
    val premiumDefaults: Set<AstraWaveEntitlement> = plusDefaults + setOf(
        AstraWaveEntitlement.LIVE_DEVICE_SYNC,
        AstraWaveEntitlement.REMOTE_CONTROL,
        AstraWaveEntitlement.SIX_PANE_MULTIVIEW,
        AstraWaveEntitlement.SERIES_RECORDING,
        AstraWaveEntitlement.CATCH_UP,
        AstraWaveEntitlement.TIMESHIFT,
        AstraWaveEntitlement.DOWNLOADS,
        AstraWaveEntitlement.TRAVEL_MODE,
        AstraWaveEntitlement.ASTRA_CONCIERGE,
        AstraWaveEntitlement.HOUSEHOLD_VOTING,
        AstraWaveEntitlement.PRIORITY_SOURCE_FAILOVER,
        AstraWaveEntitlement.SOURCE_HEALTH_HISTORY,
        AstraWaveEntitlement.ADVANCED_EPG,
        AstraWaveEntitlement.CHANNEL_EDITOR,
        AstraWaveEntitlement.UNLIMITED_PROVIDERS,
        AstraWaveEntitlement.PREMIUM_SPORTS_HUB,
        AstraWaveEntitlement.SPORTS_REMINDERS,
        AstraWaveEntitlement.PERSONAL_MEDIA_AGGREGATION,
        AstraWaveEntitlement.AUDIO_PREMIUM,
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
