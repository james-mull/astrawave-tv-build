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
import androidx.compose.foundation.verticalScroll
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
import com.astrawave.app.data.ProfileSafetyStore

@Composable
fun SafetySettingsScreen(
    profileId: String = "default",
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { ProfileSafetyStore(context) }
    var preferences by remember(profileId) { mutableStateOf(store.load(profileId)) }

    fun update(transform: (ProfileSafetyPreferences) -> ProfileSafetyPreferences) {
        preferences = transform(preferences)
        store.save(preferences)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        AstraWavePageHeader(
            title = "Privacy & Parental Controls",
            subtitle = "Profile-specific controls are stored locally. Analytics stays off unless you explicitly enable it.",
        )
        Spacer(Modifier.height(18.dp))

        Text("KIDS PROFILE", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        SafetyToggle(
            title = "Kids Profile",
            description = "Turn on profile restrictions for Live TV, Sports and external addons.",
            checked = preferences.kids.enabled,
            onCheckedChange = { enabled -> update { it.copy(kids = it.kids.copy(enabled = enabled)) } },
        )
        SafetyToggle(
            title = "Allow Live TV & Guide",
            description = "When off, Live TV and the Guide stop before any channel data is loaded.",
            checked = preferences.kids.allowLiveTv,
            enabled = preferences.kids.enabled,
            onCheckedChange = { allowed -> update { it.copy(kids = it.kids.copy(allowLiveTv = allowed)) } },
        )
        SafetyToggle(
            title = "Allow Sports",
            description = "Controls access to the Sports/Game Day schedule and matched Watch actions.",
            checked = preferences.kids.allowSports,
            enabled = preferences.kids.enabled,
            onCheckedChange = { allowed -> update { it.copy(kids = it.kids.copy(allowSports = allowed)) } },
        )
        SafetyToggle(
            title = "Allow External Addons",
            description = "When off, addon catalogs are blocked centrally from Addons, Discover and Search.",
            checked = preferences.kids.allowExternalAddons,
            enabled = preferences.kids.enabled,
            onCheckedChange = { allowed -> update { it.copy(kids = it.kids.copy(allowExternalAddons = allowed)) } },
        )
        SafetyToggle(
            title = "Require PIN to leave Kids Profile",
            description = "Policy flag for profile-exit protection. PIN transport is finalized during account/profile integration.",
            checked = preferences.kids.requirePinForProfileExit,
            enabled = preferences.kids.enabled,
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
