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
) {
    val context = LocalContext.current
    val fallbackPlan = AstraWavePlan.entries.firstOrNull { it.displayName.equals(currentPlanName, true) }
        ?: AstraWavePlan.FREE
    val repository = remember { EntitlementCloudRepository(context) }
    var entitlement by remember {
        mutableStateOf(AstraWaveEntitlementPolicy.snapshot(null, fallbackPlan, source = "account-fallback"))
    }
    var statusMessage by remember { mutableStateOf(if (repository.signedIn) "Checking your account…" else "Sign in to sync verified plan status.") }

    LaunchedEffect(currentPlanName) {
        repository.load(fallbackPlan) { result ->
            result.onSuccess { snapshot ->
                entitlement = snapshot
                statusMessage = when {
                    snapshot.source == "firestore-entitlements" -> "Verified from your AstraWave account"
                    repository.signedIn -> "No server entitlement is active yet; showing the current app plan"
                    else -> "Local plan view — sign in to restore purchases and cloud entitlements"
                }
            }.onFailure {
                statusMessage = "Could not verify plan state right now; showing your last known plan"
            }
        }
    }

    val activePlan = entitlement.plan
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AstraWavePageHeader(
            title = "Subscription & Premium",
            subtitle = "Compare plans, see your verified status, and understand what the $19.99 Premium tier unlocks.",
        )
        AstraWaveSecondaryButton(label = "← Back", onClick = onBack)

        CurrentPlanStatus(entitlement = entitlement, statusMessage = statusMessage)

        PlanCard(
            plan = AstraWavePlan.FREE,
            active = activePlan == AstraWavePlan.FREE,
            features = listOf(
                "Core movie & TV discovery",
                "Live TV and guide basics",
                "Local watchlist, favorites and history",
                "Basic sports schedule",
            ),
        )
        PlanCard(
            plan = AstraWavePlan.PLUS,
            active = activePlan == AstraWavePlan.PLUS,
            features = listOf(
                "Cloud sync and device handoff",
                "Multiview and DVR capability",
                "Advanced recommendations",
                "Extra profiles and premium themes",
            ),
        )
        PlanCard(
            plan = AstraWavePlan.PREMIUM,
            active = activePlan == AstraWavePlan.PREMIUM,
            features = listOf(
                "Everything in Plus",
                "Priority source failover",
                "Premium sports hub capability",
                "Highest-reliability playback experience",
                "Commercial-grade personalization layer",
            ),
        )

        Text(
            "Purchase and restore actions must be completed through Google Play Billing and verified by the AstraWave backend before public launch. The app now reads /entitlements/{userId} as a read-only authority, so a client cannot grant itself Premium access.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun CurrentPlanStatus(entitlement: EntitlementSnapshot, statusMessage: String) {
    val formatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(AstraWaveColors.SurfaceRaised, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("YOUR PLAN", color = AstraWaveColors.TertiaryText, style = MaterialTheme.typography.labelMedium)
        Text(entitlement.plan.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
        Text(statusMessage, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)

        entitlement.daysRemainingInTrial()?.let { days ->
            Text(
                if (days > 0) "$days day${if (days == 1) "" else "s"} left in trial" else "Trial expired",
                color = if (days > 0) AstraWaveColors.Success else AstraWaveColors.Warning,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        entitlement.trialEndsAtEpochMs?.let { end ->
            Text("Trial ends ${formatter.format(Date(end))}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        }
        entitlement.renewsAtEpochMs?.let { renewal ->
            Text("Renews ${formatter.format(Date(renewal))}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        }
        if (entitlement.premiumActive()) {
            val unlocked = entitlement.effectiveEntitlements().size
            Text("$unlocked premium capabilities active", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
            if (entitlement.has(AstraWaveEntitlement.PRIORITY_SOURCE_FAILOVER)) {
                Text("Priority failover enabled", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun PlanCard(
    plan: AstraWavePlan,
    active: Boolean,
    features: List<String>,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (active) AstraWaveColors.SurfaceFocus else AstraWaveColors.Surface,
                RoundedCornerShape(20.dp),
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(plan.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            Text(
                plan.monthlyPriceUsd?.let { "$${"%.2f".format(it)}/mo" } ?: "Free",
                color = if (plan == AstraWavePlan.PREMIUM) AstraWaveColors.Accent else AstraWaveColors.SecondaryText,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (active) {
            Text("CURRENT PLAN", color = AstraWaveColors.Success, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(2.dp))
        features.forEach { feature ->
            Text("• $feature", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
