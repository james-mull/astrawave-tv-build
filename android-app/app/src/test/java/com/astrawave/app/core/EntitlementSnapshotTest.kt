package com.astrawave.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementSnapshotTest {
    @Test
    fun premiumIsActiveBeforePaidThroughTime() {
        val now = 1_000_000L
        val snapshot = AstraWaveEntitlementPolicy.snapshot(
            userId = "user",
            plan = AstraWavePlan.PREMIUM,
            renewsAtEpochMs = now + 60_000L,
            source = "firestore-entitlements",
        )
        assertTrue(snapshot.premiumActive(now))
        assertTrue(snapshot.has(AstraWaveEntitlement.DVR, now))
    }

    @Test
    fun premiumFailsClosedAfterPaidThroughTime() {
        val now = 1_000_000L
        val snapshot = AstraWaveEntitlementPolicy.snapshot(
            userId = "user",
            plan = AstraWavePlan.PREMIUM,
            renewsAtEpochMs = now - 1L,
            source = "firestore-entitlements",
        )
        assertFalse(snapshot.premiumActive(now))
        assertFalse(snapshot.has(AstraWaveEntitlement.DVR, now))
    }

    @Test
    fun freeNeverBecomesPremium() {
        val snapshot = AstraWaveEntitlementPolicy.snapshot(
            userId = "user",
            plan = AstraWavePlan.FREE,
            renewsAtEpochMs = Long.MAX_VALUE,
        )
        assertFalse(snapshot.premiumActive(1L))
    }
}
