package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Phase 2 visual-QA surface for checking the shared AstraWave design system at
 * phone, tablet, and 10-foot TV sizes before feature modules depend on it.
 */
@Composable
private fun AstraWaveDesignSystemShowcase() {
    AstraWaveTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AstraWaveColors.Background)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            AstraWavePageHeader(
                title = "AstraWave",
                subtitle = "Shared design-system visual QA",
            )
            AstraWaveSectionHeader(
                title = "Actions",
                subtitle = "Primary and secondary controls share TV focus treatment.",
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AstraWavePrimaryButton(label = "Watch now", onClick = {})
                AstraWaveSecondaryButton(label = "Add to list", onClick = {})
            }
            AstraWaveFocusableCard(Modifier.width(240.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Focusable media card", color = AstraWaveColors.PrimaryText)
                    Text("D-pad focus should remain obvious from 10 feet.", color = AstraWaveColors.SecondaryText)
                }
            }
            AstraWaveSectionHeader(
                title = "Operational states",
                subtitle = "No feature destination should fall back to a blank or dead screen.",
            )
            AstraWaveLoadingState(
                title = "Refreshing catalogs",
                message = "Loading treatment remains readable on phone, tablet, and TV.",
            )
            AstraWaveEmptyState(
                title = "Nothing here yet",
                message = "Empty destinations explain what is missing and can offer a focused next action.",
                actionLabel = "Browse movies",
                onAction = {},
            )
            AstraWaveErrorState(
                title = "Source unavailable",
                message = "Errors explain the failure without implying that unavailable content is playable.",
                retryLabel = "Try again",
                onRetry = {},
            )
        }
    }
}

@Preview(name = "AstraWave Phone", widthDp = 393, heightDp = 852, showBackground = true)
@Composable
private fun AstraWavePhonePreview() = AstraWaveDesignSystemShowcase()

@Preview(name = "AstraWave Tablet", widthDp = 1024, heightDp = 768, showBackground = true)
@Composable
private fun AstraWaveTabletPreview() = AstraWaveDesignSystemShowcase()

@Preview(name = "AstraWave TV 1080p", widthDp = 960, heightDp = 540, showBackground = true)
@Composable
private fun AstraWaveTvPreview() = AstraWaveDesignSystemShowcase()
