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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.AstraWaveEntitlementPolicy
import com.astrawave.app.core.AstraWavePlan

@Composable
fun SubscriptionOverviewScreen(
    currentPlanName: String,
    onBack: () -> Unit,
) {
    val activePlan = AstraWavePlan.entries.firstOrNull { it.displayName.equals(currentPlanName, true) }
        ?: AstraWavePlan.FREE

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
            subtitle = "Compare AstraWave plans and see what the $19.99 Premium tier adds.",
        )
        AstraWaveSecondaryButton(label = "← Back", onClick = onBack)

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

        val premiumCount = AstraWaveEntitlementPolicy.premiumDefaults.size
        Text(
            "Premium currently exposes $premiumCount premium entitlements in the app contract. Billing purchase/restore wiring should be connected before public launch so plan state always comes from the store/backend rather than local UI state.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
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
