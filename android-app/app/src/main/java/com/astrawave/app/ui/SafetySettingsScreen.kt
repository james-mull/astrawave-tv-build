package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.ProfileSafetyPreferences
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.KidsModePolicyStore
import com.astrawave.app.data.ProfileSafetyStore

@Composable
fun SafetySettingsScreen(
    profileId: String = "default",
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { ProfileSafetyStore(context) }
    val kidsStore = remember { KidsModePolicyStore(context) }
    val household = remember { HouseholdProfileStore(context) }
    var preferences by remember(profileId) { mutableStateOf(store.load(profileId)) }
    var kidsPolicy by remember(profileId) { mutableStateOf(kidsStore.load(profileId)) }
    val householdKids = remember(profileId) { household.profiles().firstOrNull { it.id == profileId }?.kidsMode == true }
    val kidsEnabled = householdKids || preferences.kids.enabled

    fun update(transform: (ProfileSafetyPreferences) -> ProfileSafetyPreferences) {
        preferences = transform(preferences)
        store.save(preferences)
    }

    fun updateKids(transform: (KidsModePolicyStore.Policy) -> KidsModePolicyStore.Policy) {
        kidsPolicy = transform(kidsPolicy)
        kidsStore.save(profileId, kidsPolicy)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        AstraWavePageHeader(
            title = "Privacy & Parental Controls",
            subtitle = "Profile-specific controls are stored locally. Kids discovery, search and bedtime rules apply immediately to this profile.",
        )
        Spacer(Modifier.height(18.dp))

        Text("KIDS PROFILE", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        SafetyToggle(
            title = "Kids Profile",
            description = if (householdKids) "This household profile is already designated as a Kids profile." else "Turn on profile restrictions for Live TV, Sports and external addons.",
            checked = kidsEnabled,
            enabled = !householdKids,
            onCheckedChange = { enabled -> update { it.copy(kids = it.kids.copy(enabled = enabled)) } },
        )

        if (kidsEnabled) {
            Spacer(Modifier.height(12.dp))
            AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column {
                    Text("Age level", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Controls which family genres AstraWave uses for the Kids Movies and Kids TV surfaces.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KidsModePolicyStore.AgeLevel.entries.forEach { level ->
                            FilterChip(
                                selected = kidsPolicy.ageLevel == level,
                                onClick = { updateKids { it.copy(ageLevel = level) } },
                                label = { Text(level.label) },
                            )
                        }
                    }
                }
            }
            SafetyToggle(
                title = "Approved Only",
                description = "Use the narrowest curated kids discovery set and disable free-form search.",
                checked = kidsPolicy.approvedOnly,
                onCheckedChange = { enabled -> updateKids { it.copy(approvedOnly = enabled) } },
            )
            SafetyToggle(
                title = "Allow Kids Search",
                description = "Search uses built-in AstraWave metadata only; external addon results never appear in Kids Mode.",
                checked = kidsPolicy.allowSearch && !kidsPolicy.approvedOnly,
                enabled = !kidsPolicy.approvedOnly,
                onCheckedChange = { enabled -> updateKids { it.copy(allowSearch = enabled) } },
            )

            AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column {
                    Text("Bedtime", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("During bedtime AstraWave pauses Kids Movies, Kids TV and Kids Search for this profile.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !kidsPolicy.bedtimeEnabled,
                            onClick = { updateKids { it.copy(bedtimeEnabled = false) } },
                            label = { Text("Off") },
                        )
                        listOf(20 to "8 PM – 7 AM", 21 to "9 PM – 7 AM", 22 to "10 PM – 7 AM").forEach { (hour, label) ->
                            FilterChip(
                                selected = kidsPolicy.bedtimeEnabled && kidsPolicy.bedtimeStartHour == hour && kidsPolicy.bedtimeEndHour == 7,
                                onClick = { updateKids { it.copy(bedtimeEnabled = true, bedtimeStartHour = hour, bedtimeEndHour = 7) } },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }
        }

        SafetyToggle(
            title = "Allow Live TV & Guide",
            description = "When off, Live TV and the Guide stop before any channel data is loaded.",
            checked = preferences.kids.allowLiveTv,
            enabled = kidsEnabled,
            onCheckedChange = { allowed -> update { it.copy(kids = it.kids.copy(allowLiveTv = allowed)) } },
        )
        SafetyToggle(
            title = "Allow Sports",
            description = "Controls access to the Sports/Game Day schedule and matched Watch actions.",
            checked = preferences.kids.allowSports,
            enabled = kidsEnabled,
            onCheckedChange = { allowed -> update { it.copy(kids = it.kids.copy(allowSports = allowed)) } },
        )
        SafetyToggle(
            title = "Allow External Addons",
            description = "When off, addon catalogs are blocked centrally from Addons, Discover and Search.",
            checked = preferences.kids.allowExternalAddons,
            enabled = kidsEnabled,
            onCheckedChange = { allowed -> update { it.copy(kids = it.kids.copy(allowExternalAddons = allowed)) } },
        )
        SafetyToggle(
            title = "Require PIN to leave Kids Profile",
            description = "Use the household profile PIN before switching out of a protected Kids profile.",
            checked = preferences.kids.requirePinForProfileExit,
            enabled = kidsEnabled,
            onCheckedChange = { required -> update { it.copy(kids = it.kids.copy(requirePinForProfileExit = required)) } },
        )

        Spacer(Modifier.height(24.dp))
        Text("PRIVACY", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        SafetyToggle(
            title = "Local-only mode",
            description = "Prefer local state and avoid cloud sync behavior for this profile.",
            checked = preferences.privacy.localOnlyMode,
            onCheckedChange = { enabled -> update { it.copy(privacy = it.privacy.copy(localOnlyMode = enabled, cloudSyncEnabled = if (enabled) false else it.privacy.cloudSyncEnabled)) } },
        )
        SafetyToggle(
            title = "Cloud Sync",
            description = "Allow profile state to sync when Firebase/account configuration is available.",
            checked = preferences.privacy.cloudSyncEnabled,
            enabled = !preferences.privacy.localOnlyMode,
            onCheckedChange = { enabled -> update { it.copy(privacy = it.privacy.copy(cloudSyncEnabled = enabled)) } },
        )
        SafetyToggle(
            title = "Analytics",
            description = "Opt in to analytics. This remains disabled by default.",
            checked = preferences.privacy.analyticsEnabled,
            onCheckedChange = { enabled -> update { it.copy(privacy = it.privacy.copy(analyticsEnabled = enabled)) } },
        )
        SafetyToggle(
            title = "Recommendation learning",
            description = "Allow watch/listening behavior to influence personalized recommendations.",
            checked = preferences.privacy.recommendationLearningEnabled,
            onCheckedChange = { enabled -> update { it.copy(privacy = it.privacy.copy(recommendationLearningEnabled = enabled)) } },
        )
        SafetyToggle(
            title = "Save playback history",
            description = "Keep Continue Watching and playback-history records for this profile.",
            checked = preferences.privacy.savePlaybackHistory,
            onCheckedChange = { enabled -> update { it.copy(privacy = it.privacy.copy(savePlaybackHistory = enabled)) } },
        )
        SafetyToggle(
            title = "Save search history",
            description = "Allow search history to be retained for this profile.",
            checked = preferences.privacy.saveSearchHistory,
            onCheckedChange = { enabled -> update { it.copy(privacy = it.privacy.copy(saveSearchHistory = enabled)) } },
        )

        Spacer(Modifier.height(24.dp))
        AstraWavePrimaryButton("Done", onBack, Modifier.fillMaxWidth())
    }
}

@Composable
private fun SafetyToggle(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(description, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        }
    }
}
