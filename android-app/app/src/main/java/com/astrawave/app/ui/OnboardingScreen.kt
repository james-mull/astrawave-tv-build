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
        "Start Watching",
        "Use AstraWave's ready-to-use experience now. You can add your own sources later.",
        OnboardingStep.TMDB,
    ),
    LIVE_TV(
        "Add My Live TV",
        "Connect an M3U or Xtream source and build your personalized Guide.",
        OnboardingStep.LIVE_TV,
    ),
    PERSONAL_MEDIA(
        "Add My Media",
        "Connect Plex, Jellyfin, Emby, WebDAV or another authorized personal library.",
        OnboardingStep.PERSONAL_MEDIA,
    ),
    EVERYTHING(
        "Set Up Everything",
        "Configure profiles, sources, audio, devices and privacy options step by step.",
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
    var showAdvanced by remember { mutableStateOf(false) }
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
            QuickStartPreset.EVERYTHING -> {
                showAdvanced = true
                preset.target?.let(::routeTo)
            }
            else -> preset.target?.let(::routeTo)
        }
    }

    fun completeCurrent() {
        if (state.currentStep == OnboardingStep.COMPLETE) {
            onFinished()
            return
        }
        state = store.markComplete(profileId, state.currentStep)
        refreshHealth()
        if (state.complete) onFinished()
    }

    fun skipCurrent() {
        if (state.currentStep == OnboardingStep.WELCOME || state.currentStep == OnboardingStep.COMPLETE) return
        state = store.skip(profileId, state.currentStep)
        refreshHealth()
        if (state.complete) onFinished()
    }

    val actionableSteps = OnboardingFlow.orderedSteps.filterNot { it == OnboardingStep.COMPLETE }
    val doneCount = actionableSteps.count(state::isDone)
    val missingRequired = health.items.filter { !it.optional && !it.ready }
    val optionalSuggestions = health.items
        .filter { it.optional && !it.ready }
        .sortedBy { recommendationPriority(it.id) }
        .take(3)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background)
            .padding(20.dp),
    ) {
        Text("ASTRAWAVE", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        AstraWavePageHeader(
            title = if (health.readyToWatch) "You’re ready." else "Let’s get you watching.",
            subtitle = if (health.readyToWatch) {
                "AstraWave is ready for normal use. Everything below is optional."
            } else {
                "Choose what you want to do first. You do not need to configure every feature."
            },
        )

        Spacer(Modifier.height(18.dp))
        AstraWaveStatePanel(
            title = if (health.readyToWatch) "Ready to Watch" else "Setup ${health.score}%",
            message = if (health.readyToWatch) {
                "Core playback is available. Add personal sources only when you want them."
            } else {
                "${health.readyCount} of ${health.requiredCount} required checks are ready."
            },
            tone = if (health.readyToWatch) AstraWaveStateTone.SUCCESS else AstraWaveStateTone.ACCENT,
        )

        if (health.readyToWatch) {
            Spacer(Modifier.height(14.dp))
            AstraWavePrimaryButton("Enter AstraWave", onFinished, Modifier.fillMaxWidth())
        }

        Spacer(Modifier.height(22.dp))
        AstraWaveSectionHeader("Get Started", "Pick one path. You can change anything later.")
        Spacer(Modifier.height(8.dp))
        QuickStartPreset.entries.forEach { preset ->
            AstraWaveFocusableCard(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { chooseQuickStart(preset) },
            ) {
                Column {
                    Text(preset.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(3.dp))
                    Text(preset.description, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        if (missingRequired.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            AstraWaveSectionHeader("Needs Attention", "Only the items blocking the core experience")
            Spacer(Modifier.height(8.dp))
            missingRequired.forEach { item ->
                AstraWaveActionRow(
                    title = item.title,
                    subtitle = item.detail,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text("FIX", color = AstraWaveColors.Warning, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            AstraWaveSecondaryButton(
                label = if (repairing) "Checking Setup…" else "Fix Safe Setup Issues",
                onClick = {
                    if (repairing) return@AstraWaveSecondaryButton
                    repairing = true
                    repairMessage = null
                    scope.launch {
                        val report = runCatching { repairRepository.repair(profileId) }
                        repairing = false
                        repairMessage = report.fold(
                            onSuccess = { it.messages.joinToString(" ") },
                            onFailure = { it.message ?: "Safe setup repair could not complete." },
                        )
                        refreshHealth()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            repairMessage?.let {
                Spacer(Modifier.height(8.dp))
                AstraWaveStatePanel("Setup Result", it)
            }
        }

        if (health.readyToWatch && optionalSuggestions.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            AstraWaveSectionHeader("Optional Upgrades", "Useful additions, not requirements")
            Spacer(Modifier.height(8.dp))
            optionalSuggestions.forEach { item ->
                val target = recommendationTarget(item.id)
                AstraWaveActionRow(
                    title = item.title,
                    subtitle = item.detail,
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    Text(
                        if (target == null) "LATER" else "ADD",
                        color = if (target == null) AstraWaveColors.TertiaryText else AstraWaveColors.Accent,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = if (target == null) Modifier.padding(8.dp) else Modifier.clickable { routeTo(target) }.padding(8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        AstraWaveSecondaryButton(
            label = if (showAdvanced) "Hide Advanced Setup" else "Advanced Setup",
            onClick = { showAdvanced = !showAdvanced },
            modifier = Modifier.fillMaxWidth(),
        )

        if (showAdvanced) {
            Spacer(Modifier.height(14.dp))
            AstraWaveSectionHeader("Advanced Setup", "$doneCount of ${actionableSteps.size} steps complete")
            Spacer(Modifier.height(8.dp))
            actionableSteps.forEach { step ->
                val completed = step in state.completedSteps
                val skipped = step in state.skippedSteps
                val current = state.currentStep == step
                AstraWaveActionRow(
                    title = stepTitle(step),
                    subtitle = stepDescription(step),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
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

            Spacer(Modifier.height(12.dp))
            if (state.complete) {
                AstraWavePrimaryButton("Finish Setup", onFinished, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text(
                    "Restart advanced setup",
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
                    label = if (state.currentStep == OnboardingStep.WELCOME) "Start Advanced Setup" else "Mark Current Step Complete",
                    onClick = ::completeCurrent,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.currentStep != OnboardingStep.WELCOME) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Skip this step",
                        color = AstraWaveColors.SecondaryText,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable(onClick = ::skipCurrent).padding(8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

private fun recommendationPriority(id: String): Int = when (id) {
    "live" -> 1
    "devices" -> 2
    "audio" -> 3
    "dvr" -> 4
    "cloud" -> 5
    "travel" -> 6
    else -> 99
}

private fun recommendationTarget(id: String): OnboardingStep? = when (id) {
    "live", "dvr" -> OnboardingStep.LIVE_TV
    "audio" -> OnboardingStep.AUDIO
    "devices" -> OnboardingStep.DEVICE_PAIRING
    else -> null
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
    OnboardingStep.TMDB -> "Enable movie and TV metadata discovery."
    OnboardingStep.LIVE_TV -> "Connect your own M3U/Xtream source or use eligible AstraWave Free TV."
    OnboardingStep.ADDONS -> "Enable compatible addons you trust for catalogs and metadata."
    OnboardingStep.PERSONAL_MEDIA -> "Connect your own Jellyfin, Emby, Plex, WebDAV or NAS library."
    OnboardingStep.AUDIO -> "Add podcasts, radio and other audio sources."
    OnboardingStep.DEVICE_PAIRING -> "Pair a phone, TV or companion device for remote control and handoff."
    OnboardingStep.PRIVACY -> "Review sync, telemetry and family controls."
    OnboardingStep.COMPLETE -> "Setup complete."
}
