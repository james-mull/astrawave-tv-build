package com.astrawave.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreInvariantTest {

    @Test
    fun localOnlyPrivacyDisablesCloudAndAnalytics() {
        val preferences = ProfileSafetyPreferences(
            profileId = "adult",
            privacy = PrivacyPreferences(
                analyticsEnabled = true,
                cloudSyncEnabled = true,
                localOnlyMode = true,
            ),
        ).normalized()

        assertFalse(ProfileSafetyPolicy.cloudSyncAllowed(preferences))
        assertFalse(ProfileSafetyPolicy.analyticsAllowed(preferences))
    }

    @Test
    fun accessibilityTextScaleIsClampedToSupportedRange() {
        val large = AccessibilityPreferences(textScale = 5f).normalized()
        val small = AccessibilityPreferences(textScale = 0.1f).normalized()

        assertEquals(AccessibilityPreferences.MAX_TEXT_SCALE, large.textScale)
        assertEquals(AccessibilityPreferences.MIN_TEXT_SCALE, small.textScale)
    }

    @Test
    fun expiredTrialDoesNotGrantPlusEntitlements() {
        val snapshot = AstraWaveEntitlementPolicy.snapshot(
            userId = "user",
            plan = AstraWavePlan.PLUS,
            trialEndsAtEpochMs = 1_000L,
            renewsAtEpochMs = null,
        )

        assertFalse(snapshot.has(AstraWaveEntitlement.MULTIVIEW, atEpochMs = 1_001L))
        assertTrue(snapshot.has(AstraWaveEntitlement.MULTIVIEW, atEpochMs = 999L))
    }

    @Test
    fun premiumPlanCarriesCommercialReliabilityEntitlements() {
        val snapshot = AstraWaveEntitlementPolicy.snapshot(
            userId = "premium-user",
            plan = AstraWavePlan.PREMIUM,
        )

        assertEquals(19.99, AstraWavePlan.PREMIUM.monthlyPriceUsd ?: 0.0, 0.001)
        assertTrue(snapshot.has(AstraWaveEntitlement.MULTIVIEW))
        assertTrue(snapshot.has(AstraWaveEntitlement.ADVANCED_RECOMMENDATIONS))
        assertTrue(snapshot.has(AstraWaveEntitlement.PRIORITY_SOURCE_FAILOVER))
        assertTrue(snapshot.has(AstraWaveEntitlement.PREMIUM_SPORTS_HUB))
    }

    @Test
    fun onboardingNeedsProfilePrivacyAndUsableEntertainmentPath() {
        val done = OnboardingState(
            completedSteps = setOf(OnboardingStep.PROFILE, OnboardingStep.PRIVACY),
            currentStep = OnboardingStep.PRIVACY,
        )
        val notReady = SetupReadiness(
            profileReady = true,
            discoveryReady = false,
            liveTvReady = false,
            addonsReady = false,
            personalMediaReady = false,
            audioReady = false,
            devicePairingReady = false,
        )
        val ready = notReady.copy(discoveryReady = true)

        assertFalse(OnboardingFlow.canComplete(done, notReady))
        assertTrue(OnboardingFlow.canComplete(done, ready))
    }

    @Test
    fun newerBackupSchemaAndCorruptProgressAreRejected() {
        val futureBackup = AstraWaveBackup(
            schemaVersion = AstraWaveBackup.CURRENT_SCHEMA_VERSION + 1,
            exportedAtEpochMs = 10L,
        )
        assertFalse(BackupValidation.canImport(futureBackup))

        val corruptProgress = AstraWaveBackup(
            exportedAtEpochMs = 10L,
            progress = listOf(
                BackupProgress(
                    profileId = "adult",
                    mediaId = "movie:1",
                    positionMs = 2_000L,
                    durationMs = 1_000L,
                ),
            ),
        )
        assertFalse(BackupValidation.canImport(corruptProgress))
    }

    @Test
    fun featureFlagMinimumVersionIsEnforced() {
        val provider = object : FeatureFlagProvider {
            override fun flags(): List<FeatureFlag> = listOf(
                FeatureFlag(key = "new-player", enabled = true, minimumVersion = "1.2.0"),
            )
        }

        assertFalse(provider.enabled("new-player", "1.1.9"))
        assertTrue(provider.enabled("new-player", "1.2.0"))
        assertTrue(provider.enabled("new-player", "1.3.0"))
    }
}
