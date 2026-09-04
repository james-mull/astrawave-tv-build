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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.ProfileSafetyPreferences
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.KidsModePolicyStore
import com.astrawave.app.data.ParentPinStore
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
    val parentPin = remember { ParentPinStore(context) }
    var preferences by remember(profileId) { mutableStateOf(store.load(profileId)) }
    var kidsPolicy by remember(profileId) { mutableStateOf(kidsStore.load(profileId)) }
    val householdKids = remember(profileId) { household.profiles().firstOrNull { it.id == profileId }?.kidsMode == true }
    val kidsEnabled = householdKids || preferences.kids.enabled
    var parentUnlocked by remember(profileId) { mutableStateOf(!kidsEnabled || !parentPin.hasPin()) }
    var editParentPin by remember { mutableStateOf(false) }

    fun update(transform: (ProfileSafetyPreferences) -> ProfileSafetyPreferences) {
        preferences = transform(preferences)
        store.save(preferences)
    }

    fun updateKids(transform: (KidsModePolicyStore.Policy) -> KidsModePolicyStore.Policy) {
        kidsPolicy = transform(kidsPolicy)
        kidsStore.save(profileId, kidsPolicy)
    }

    if (kidsEnabled && parentPin.hasPin() && !parentUnlocked) {
        ParentControlsUnlockDialog(
            onDismiss = onBack,
            verify = parentPin::verify,
            onVerified = { parentUnlocked = true },
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(24.dp),
    ) {
        AstraWavePageHeader(
            title = "Privacy & Parental Controls",
            subtitle = "Profile-specific controls are stored locally. Kids ratings, approvals, search and bedtime rules apply immediately.",
        )
        Spacer(Modifier.height(18.dp))

        Text("KIDS PROFILE", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        SafetyToggle(
            title = "Kids Profile",
            description = if (householdKids) "This household profile is already designated as a Kids profile." else "Turn on Kids restrictions for this profile.",
            checked = kidsEnabled,
            enabled = !householdKids,
            onCheckedChange = { enabled -> update { it.copy(kids = it.kids.copy(enabled = enabled)) } },
        )

        if (kidsEnabled) {
            Spacer(Modifier.height(12.dp))
            AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column {
                    Text("Household Parent PIN", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (parentPin.hasPin()) "Required to leave protected Kids Mode, approve individual titles and change these controls."
                        else "Create a 4–6 digit Parent PIN before using Approved Only or protected Kids exit.",
                        color = AstraWaveColors.SecondaryText,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    AstraWaveSecondaryButton(
                        label = if (parentPin.hasPin()) "Change Parent PIN" else "Set Parent PIN",
                        onClick = { editParentPin = true },
                    )
                }
            }

            AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column {
                    Text("Age & rating level", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("Little Kids allows G/TV-Y style content; Kids adds PG/TV-PG; Older Kids adds PG-13/TV-14.", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
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
                description = "Only titles explicitly approved with the household Parent PIN can be opened. Unapproved titles remain visibly locked for parent review.",
                checked = kidsPolicy.approvedOnly,
                enabled = parentPin.hasPin(),
                onCheckedChange = { enabled -> updateKids { it.copy(approvedOnly = enabled, allowSearch = if (enabled) false else it.allowSearch) } },
            )
            SafetyToggle(
                title = "Allow Unrated Titles",
                description = "When off, unrated/unknown-certification titles are hidden unless a parent explicitly approves them.",
                checked = kidsPolicy.allowUnrated,
                enabled = !kidsPolicy.approvedOnly,
                onCheckedChange = { enabled -> updateKids { it.copy(allowUnrated = enabled) } },
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
                    Text("Parent-approved titles", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text("${kidsStore.approvedTitleIds(profileId).size} approved • ${kidsStore.blockedTitleIds(profileId).size} blocked", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
                    if (kidsStore.approvedTitleIds(profileId).isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        AstraWaveSecondaryButton(
                            label = "Clear Approved Titles",
                            onClick = {
                                kidsStore.approvedTitleIds(profileId).forEach { kidsStore.approveTitle(profileId, it, false) }
                            },
                        )
                    }
                }
            }

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
            title = "Require Parent PIN to leave Kids Profile",
            description = "Prevents switching from this Kids profile into an adult profile without the household Parent PIN.",
            checked = preferences.kids.requirePinForProfileExit,
            enabled = kidsEnabled && parentPin.hasPin(),
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

    if (editParentPin) {
        ParentPinEditorDialog(
            hasExistingPin = parentPin.hasPin(),
            verifyExisting = parentPin::verify,
            onDismiss = { editParentPin = false },
            onSave = { newPin ->
                if (parentPin.setPin(newPin)) {
                    parentUnlocked = true
                    editParentPin = false
                }
            },
        )
    }
}

@Composable
private fun ParentControlsUnlockDialog(
    onDismiss: () -> Unit,
    verify: (String) -> Boolean,
    onVerified: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Parent PIN required") },
        text = {
            Column {
                Text("Enter the household Parent PIN to change Kids Mode settings.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6); error = false },
                    label = { Text("Parent PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (error) {
                    Spacer(Modifier.height(6.dp))
                    Text("Incorrect Parent PIN", color = AstraWaveColors.Error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (verify(pin)) onVerified() else error = true }) { Text("Unlock") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ParentPinEditorDialog(
    hasExistingPin: Boolean,
    verifyExisting: (String) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (hasExistingPin) "Change Parent PIN" else "Set Parent PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (hasExistingPin) {
                    OutlinedTextField(
                        value = currentPin,
                        onValueChange = { currentPin = it.filter(Char::isDigit).take(6); error = null },
                        label = { Text("Current Parent PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it.filter(Char::isDigit).take(6); error = null },
                    label = { Text("New 4–6 digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(6); error = null },
                    label = { Text("Confirm PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                error?.let { Text(it, color = AstraWaveColors.Error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    hasExistingPin && !verifyExisting(currentPin) -> error = "Current Parent PIN is incorrect"
                    newPin.length < 4 -> error = "PIN must be 4–6 digits"
                    newPin != confirmPin -> error = "PINs do not match"
                    else -> onSave(newPin)
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
            Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        }
    }
}
