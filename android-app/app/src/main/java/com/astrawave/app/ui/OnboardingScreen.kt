package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.OnboardingFlow
import com.astrawave.app.core.OnboardingState
import com.astrawave.app.core.OnboardingStep
import com.astrawave.app.data.OnboardingStore

@Composable
fun AstraWaveOnboardingScreen(
    profileId: String = "default",
    onOpenStep: (OnboardingStep) -> Unit = {},
    onFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { OnboardingStore(context) }
    var state by remember(profileId) { mutableStateOf(store.load(profileId)) }

    fun completeCurrent() {
        if (state.currentStep == OnboardingStep.COMPLETE) {
            onFinished()
        } else {
            state = store.markComplete(profileId, state.currentStep)
            if (state.complete) onFinished()
        }
    }

    fun skipCurrent() {
        if (state.currentStep != OnboardingStep.WELCOME && state.currentStep != OnboardingStep.COMPLETE) {
            state = store.skip(profileId, state.currentStep)
            if (state.complete) onFinished()
        }
    }

    val actionableSteps = OnboardingFlow.orderedSteps.filterNot { it == OnboardingStep.COMPLETE }
    val doneCount = actionableSteps.count(state::isDone)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        Text("ASTRAWAVE SETUP", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.complete) "You’re ready to watch." else "Set up AstraWave your way.",
            color = AstraWaveColors.PrimaryText,
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.complete) {
                "Your setup progress is saved. You can revisit any connection or preference whenever you want."
            } else {
                "AstraWave saves each step automatically, so you can stop and continue later on this device."
            },
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(18.dp))
        AstraWaveStatePanel(
            title = "$doneCount of ${actionableSteps.size} setup steps finished",
            message = if (state.complete) "Setup complete" else "Current step: ${stepTitle(state.currentStep)}",
        )
        Spacer(Modifier.height(18.dp))

        actionableSteps.forEach { step ->
            val completed = step in state.completedSteps
            val skipped = step in state.skippedSteps
            val current = state.currentStep == step
            AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(stepTitle(step), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(stepDescription(step), color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        when {
                            completed -> "DONE"
                            skipped -> "SKIPPED"
                            current -> "CURRENT"
                            else -> "OPEN"
                        },
                        color = when {
                            completed -> AstraWaveColors.Success
                            current -> AstraWaveColors.Accent
                            skipped -> AstraWaveColors.TertiaryText
                            else -> AstraWaveColors.SecondaryText
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable {
                            state = store.goTo(profileId, step)
                            onOpenStep(step)
                        }.padding(8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        if (state.complete) {
            AstraWavePrimaryButton("Finish", onFinished, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                "Restart setup",
                color = AstraWaveColors.Warning,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable {
                    store.reset(profileId)
                    state = store.load(profileId)
                }.padding(8.dp),
            )
        } else {
            AstraWavePrimaryButton(
                label = if (state.currentStep == OnboardingStep.WELCOME) "Start Setup" else "Mark Step Complete",
                onClick = ::completeCurrent,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.currentStep != OnboardingStep.WELCOME) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Skip for now",
                    color = AstraWaveColors.SecondaryText,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = ::skipCurrent).padding(8.dp),
                )
            }
        }
    }
}

private fun stepTitle(step: OnboardingStep): String = when (step) {
    OnboardingStep.WELCOME -> "Welcome"
    OnboardingStep.PROFILE -> "Profile & Household"
    OnboardingStep.TMDB -> "Movies & TV Discovery"
    OnboardingStep.LIVE_TV -> "Live TV Sources"
    OnboardingStep.ADDONS -> "Extensions & Addons"
    OnboardingStep.PERSONAL_MEDIA -> "Personal Media"
    OnboardingStep.AUDIO -> "Music & Podcasts"
    OnboardingStep.DEVICE_PAIRING -> "Devices & Remote"
    OnboardingStep.PRIVACY -> "Privacy & Preferences"
    OnboardingStep.COMPLETE -> "Complete"
}

private fun stepDescription(step: OnboardingStep): String = when (step) {
    OnboardingStep.WELCOME -> "Choose only the parts of AstraWave you want to configure now."
    OnboardingStep.PROFILE -> "Set the active profile and household preferences."
    OnboardingStep.TMDB -> "Enable built-in movie and TV metadata discovery."
    OnboardingStep.LIVE_TV -> "Connect your own M3U/Xtream source or use eligible AstraWave Free TV."
    OnboardingStep.ADDONS -> "Install compatible addons you trust for catalogs and metadata."
    OnboardingStep.PERSONAL_MEDIA -> "Connect your own Jellyfin, Emby, Plex, WebDAV or NAS library."
    OnboardingStep.AUDIO -> "Add podcasts, radio and other audio sources."
    OnboardingStep.DEVICE_PAIRING -> "Pair a phone, TV or companion device for remote control and handoff."
    OnboardingStep.PRIVACY -> "Review local-only, sync, telemetry and family controls."
    OnboardingStep.COMPLETE -> "Setup complete."
}
