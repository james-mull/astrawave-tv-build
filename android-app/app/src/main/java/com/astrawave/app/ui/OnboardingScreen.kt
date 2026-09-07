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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.OnboardingFlow
import com.astrawave.app.core.OnboardingStep
import com.astrawave.app.data.OnboardingStore
import com.astrawave.app.data.SetupHealthRepository
import com.astrawave.app.data.SetupRepairRepository
import kotlinx.coroutines.launch

private enum class QuickStartPreset(
    val title: String,
    val description: String,
    val target: OnboardingStep?,
) {
    JUST_WATCH(
        "Just Watch",
        "Movies, TV, discovery and built-in eligible sources. Skip advanced setup for now.",
        OnboardingStep.TMDB,
    ),
    LIVE_TV(
        "Live TV",
        "Connect M3U/Xtream, build the Guide and optionally enable DVR later.",
        OnboardingStep.LIVE_TV,
    ),
    PERSONAL_MEDIA(
        "Personal Media",
        "Connect Plex, Jellyfin, Emby, WebDAV or your own media server.",
        OnboardingStep.PERSONAL_MEDIA,
    ),
    EVERYTHING(
        "Everything",
        "Walk through the complete AstraWave setup with profiles, sources, audio, devices and privacy.",
        OnboardingStep.PROFILE,
    ),
}

@Composable
fun AstraWaveOnboardingScreen(
    profileId: String = "default",
    onOpenStep: (OnboardingStep) -> Unit = {},
    onFinished: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { OnboardingStore(context) }
    val healthRepository = remember { SetupHealthRepository(context) }
    val repairRepository = remember { SetupRepairRepository(context) }
    var state by remember(profileId) { mutableStateOf(store.load(profileId)) }
    var health by remember(profileId) { mutableStateOf(healthRepository.snapshot(profileId)) }
    var repairing by remember { mutableStateOf(false) }
    var repairMessage by remember { mutableStateOf<String?>(null) }

    fun refreshHealth() {
        health = healthRepository.snapshot(profileId)
    }

    fun routeTo(step: OnboardingStep) {
        state = store.goTo(profileId, step)
        onOpenStep(step)
        refreshHealth()
    }

    fun chooseQuickStart(preset: QuickStartPreset) {
        when (preset) {
            QuickStartPreset.JUST_WATCH -> {
                if (health.readyToWatch) onFinished() else routeTo(OnboardingStep.TMDB)
            }
            else -> preset.target?.let(::routeTo)
        }
    }

    fun completeCurrent() {
        if (state.currentStep == OnboardingStep.COMPLETE) {
            onFinished()
        } else {
            state = store.markComplete(profileId, state.currentStep)
            refreshHealth()
            if (state.complete) onFinished()
        }
    }

    fun skipCurrent() {
        if (state.currentStep != OnboardingStep.WELCOME && state.currentStep != OnboardingStep.COMPLETE) {
            state = store.skip(profileId, state.currentStep)
            refreshHealth()
            if (state.complete) onFinished()
        }
    }

    val actionableSteps = OnboardingFlow.orderedSteps.filterNot { it == OnboardingStep.COMPLETE }
    val doneCount = actionableSteps.count(state::isDone)

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(24.dp),
    ) {
        Text("ASTRAWAVE SETUP", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (health.readyToWatch) "You’re ready to watch." else "How do you want to use AstraWave?",
            color = AstraWaveColors.PrimaryText,
            style = MaterialTheme.typography.headlineLarge,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (health.readyToWatch) {
                "Core playback is ready. You can enter AstraWave now or add optional features below."
            } else {
                "Pick a Quick Start. AstraWave sends you only to the setup that matters for that experience."
            },
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(18.dp))
        AstraWaveSectionHeader("Quick Start", "No technical checklist required")
        Spacer(Modifier.height(8.dp))
        QuickStartPreset.entries.forEach { preset ->
            AstraWaveFocusableCard(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { chooseQuickStart(preset) },
            ) {
                Column {
                    Text(preset.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(3.dp))
                    Text(preset.description, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        AstraWaveStatePanel(
            title = "Setup Health • ${health.score}%",
            message = if (health.readyToWatch) {
                "Core playback setup is ready"
            } else {
                "${health.readyCount} of ${health.requiredCount} required checks ready"
            },
        )
        Spacer(Modifier.height(10.dp))

        health.items.forEach { item ->
            AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(3.dp))
                        Text(item.detail, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        when {
                            item.ready -> "READY"
                            item.optional -> "OPTIONAL"
                            else -> "FIX"
                        },
                        color = when {
                            item.ready -> AstraWaveColors.Success
                            item.optional -> AstraWaveColors.TertiaryText
                            else -> AstraWaveColors.Warning
                        },
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        AstraWaveSecondaryButton(
            label = if (repairing) "Fixing Setup…" else "Fix Everything Safe",
            onClick = {
                if (repairing) return@AstraWaveSecondaryButton
                repairing = true
                repairMessage = null
                scope.launch {
                    val report = runCatching { repairRepository.repair(profileId) }
                    repairing = false
                    repairMessage = report.fold(
                        onSuccess = { it.messages.joinToString(" ") },
                        onFailure = { it.message ?: "Safe repair pass could not complete." },
                    )
                    refreshHealth()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Safe repair only: source health/counts, duplicate local sources and Travel Mode bounds. Passwords, debrid tokens, DVR consent, parental controls and subscription state are never changed.",
            color = AstraWaveColors.TertiaryText,
            style = MaterialTheme.typography.bodySmall,
        )
        repairMessage?.let {
            Spacer(Modifier.height(8.dp))
            AstraWaveStatePanel("Repair Result", it)
        }

        if (health.readyToWatch) {
            Spacer(Modifier.height(16.dp))
            AstraWavePrimaryButton("Use AstraWave Now", onFinished, Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Text(
                "Advanced setup is optional. Add Live TV, DVR, personal media, devices, debrid, addons or Travel Mode later from My AstraWave.",
                color = AstraWaveColors.TertiaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))
        AstraWaveSectionHeader("Advanced Guided Setup", "For users who want to configure everything manually")
        Spacer(Modifier.height(8.dp))
        AstraWaveStatePanel(
            title = "$doneCount of ${actionableSteps.size} guided steps finished",
            message = if (state.complete) "Guided setup complete" else "Current step: ${stepTitle(state.currentStep)}",
        )
        Spacer(Modifier.height(12.dp))

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
                        modifier = Modifier.clickable { routeTo(step) }.padding(8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        if (state.complete) {
            AstraWavePrimaryButton("Finish", onFinished, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                "Restart guided setup",
                color = AstraWaveColors.Warning,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable {
                    store.reset(profileId)
                    state = store.load(profileId)
                    refreshHealth()
                }.padding(8.dp),
            )
        } else {
            AstraWavePrimaryButton(
                label = if (state.currentStep == OnboardingStep.WELCOME) "Start Guided Setup" else "Mark Step Complete",
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
