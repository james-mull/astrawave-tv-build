package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.AstraWaveEntitlement
import com.astrawave.app.core.AstraWaveEntitlementPolicy
import com.astrawave.app.core.AstraWavePlan
import com.astrawave.app.core.EntitlementSnapshot
import com.astrawave.app.data.EntitlementCloudRepository
import java.text.DateFormat
import java.util.Date

@Composable
fun SubscriptionOverviewScreen(
    currentPlanName: String,
    onBack: () -> Unit,
    profileId: String = "default",
) {
    val context = LocalContext.current
    val fallbackPlan = AstraWavePlan.entries.firstOrNull { it.displayName.equals(currentPlanName, true) } ?: AstraWavePlan.FREE
    val repository = remember { EntitlementCloudRepository(context) }
    var entitlement by remember { mutableStateOf(AstraWaveEntitlementPolicy.snapshot(null, fallbackPlan, source = "account-fallback")) }
    var statusMessage by remember { mutableStateOf(if (repository.signedIn) "Checking your account…" else "Sign in to sync verified plan status.") }
    var showPowerCenter by remember { mutableStateOf(false) }

    if (showPowerCenter) {
        Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
            AstraWaveSecondaryButton(label = "← Subscription", onClick = { showPowerCenter = false }, modifier = Modifier.padding(18.dp))
            AstraWavePremiumPowerCenter(profileId = profileId)
        }
        return
    }

    LaunchedEffect(currentPlanName) {
        repository.load(fallbackPlan) { result ->
            result.onSuccess { snapshot ->
                entitlement = snapshot
                statusMessage = when {
                    snapshot.source == "firestore-entitlements" -> "Verified from your AstraWave account"
                    repository.signedIn -> "No server entitlement is active yet; showing the current app plan"
                    else -> "Local plan view — sign in to restore purchases and cloud entitlements"
                }
            }.onFailure { statusMessage = "Could not verify plan state right now; showing your last known plan" }
        }
    }

    val activePlan = entitlement.plan
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ASTRAWAVE+", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        AstraWavePageHeader(
            title = "$19.99 should feel obvious.",
            subtitle = "Premium is not access to content. It is the intelligence, reliability, household, sports, cloud and power-TV layer that turns your authorized entertainment sources into one polished system.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AstraWaveSecondaryButton(label = "← Back", onClick = onBack)
            AstraWavePrimaryButton(label = "Open Premium Power Center", onClick = { showPowerCenter = true })
        }

        CurrentPlanStatus(entitlement = entitlement, statusMessage = statusMessage)

        PlanCard(
            plan = AstraWavePlan.FREE,
            active = activePlan == AstraWavePlan.FREE,
            headline = "A great free entertainment client",
            features = listOf(
                "Movies & TV metadata and core discovery",
                "Basic Live TV + EPG from authorized/public sources",
                "One personal source and basic Stremio catalogs",
                "Search, watchlist, favorites, history and audio basics",
                "Basic sports schedule",
            ),
        )
        PlanCard(
            plan = AstraWavePlan.PLUS,
            active = activePlan == AstraWavePlan.PLUS,
            headline = "Cloud convenience and advanced viewing",
            features = listOf(
                "Web Control Center + cloud sync",
                "Device handoff and 2/3/4-up Multiview",
                "DVR capability on compatible authorized sources",
                "Advanced recommendations",
                "Extra household profiles and premium themes",
            ),
        )
        PlanCard(
            plan = AstraWavePlan.PREMIUM,
            active = activePlan == AstraWavePlan.PREMIUM,
            headline = "The complete AstraWave Entertainment OS",
            features = listOf(
                "Astra AI concierge for movies, TV, sports and short-watch discovery",
                "Predictive Smart Source Fusion with health history and priority failover",
                "Advanced timeline EPG, channel editor, catch-up and timeshift",
                "Record once + series recording on capable providers",
                "Unlimited compatible providers and advanced source management",
                "Sports Intelligence: My Teams, event-to-channel matching, reminders and Sports Mosaic",
                "Up to six simultaneous sports/live panes where hardware permits",
                "Downloads + Travel Mode for authorized offline media",
                "Plex, Jellyfin, Emby, WebDAV/NAS personal-media aggregation",
                "Household Watch Night voting and shared entertainment decisions",
                "Live web → TV configuration sync and private remote commands",
                "Premium audio/podcast tools and richer listening continuity",
                "Best-source autoplay, player diagnostics, PiP, track controls and backup recovery",
            ),
        )

        AstraWaveFocusableCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Why Premium exists", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                Text("The subscription pays for software intelligence and cloud orchestration: source health, sync, household state, sports personalization, remote management, AI-assisted organization and ongoing compatibility work. AstraWave does not sell third-party channels or unauthorized media.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Text(
            "Purchase and restore actions must be completed through Google Play Billing and verified by the AstraWave backend before public launch. /entitlements/{userId} remains read-only to clients, so the app cannot grant itself Premium access.",
            color = AstraWaveColors.TertiaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CurrentPlanStatus(entitlement: EntitlementSnapshot, statusMessage: String) {
    val formatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(
        Modifier.fillMaxWidth().background(AstraWaveColors.SurfaceRaised, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("YOUR PLAN", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
        Text(entitlement.plan.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
        Text(statusMessage, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        entitlement.daysRemainingInTrial()?.let { days -> Text(if (days > 0) "$days day${if (days == 1) "" else "s"} left in trial" else "Trial expired", color = if (days > 0) AstraWaveColors.Success else AstraWaveColors.Warning, style = MaterialTheme.typography.labelLarge) }
        entitlement.trialEndsAtEpochMs?.let { end -> Text("Trial ends ${formatter.format(Date(end))}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium) }
        entitlement.renewsAtEpochMs?.let { renewal -> Text("Renews ${formatter.format(Date(renewal))}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium) }
        if (entitlement.premiumActive()) {
            Text("${entitlement.effectiveEntitlements().size} premium capabilities active", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
            if (entitlement.has(AstraWaveEntitlement.PRIORITY_SOURCE_FAILOVER)) Text("Predictive source failover enabled", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun PlanCard(plan: AstraWavePlan, active: Boolean, headline: String, features: List<String>) {
    Column(
        Modifier.fillMaxWidth().background(if (active) AstraWaveColors.SurfaceFocus else AstraWaveColors.Surface, RoundedCornerShape(20.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text(plan.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
                Text(headline, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            }
            Text(plan.monthlyPriceUsd?.let { "$${"%.2f".format(it)}/mo" } ?: "Free", color = if (plan == AstraWavePlan.PREMIUM) AstraWaveColors.AccentStrong else AstraWaveColors.SecondaryText, style = MaterialTheme.typography.titleMedium)
        }
        if (active) Text("CURRENT PLAN", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(2.dp))
        features.forEach { feature -> Text("• $feature", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium) }
    }
}
