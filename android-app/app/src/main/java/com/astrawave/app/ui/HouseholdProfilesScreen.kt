package com.astrawave.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.ParentPinStore
import com.astrawave.app.data.ProfileSafetyStore

@Composable
fun HouseholdProfilesScreen(
    activeProfileId: String,
    onProfileChanged: (HouseholdProfileStore.Profile) -> Unit,
    onBack: () -> Unit,
    launchMode: Boolean = false,
) {
    val context = LocalContext.current
    val store = remember { HouseholdProfileStore(context) }
    val parentPin = remember { ParentPinStore(context) }
    val safety = remember { ProfileSafetyStore(context) }
    var profiles by remember { mutableStateOf(store.profiles()) }
    var createProfile by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<HouseholdProfileStore.Profile?>(null) }
    var pinTarget by remember { mutableStateOf<HouseholdProfileStore.Profile?>(null) }
    var parentGateTarget by remember { mutableStateOf<HouseholdProfileStore.Profile?>(null) }
    var missingParentPinTarget by remember { mutableStateOf<HouseholdProfileStore.Profile?>(null) }
    var deleteTarget by remember { mutableStateOf<HouseholdProfileStore.Profile?>(null) }
    var promptAtLaunch by remember { mutableStateOf(store.shouldPromptAtLaunch() || store.profiles().size > 1) }

    fun refresh() { profiles = store.profiles() }

    fun completeSwitch(profile: HouseholdProfileStore.Profile) {
        if (profile.pinProtected) pinTarget = profile
        else if (store.setActive(profile.id)) onProfileChanged(profile)
    }

    fun choose(profile: HouseholdProfileStore.Profile) {
        if (profile.id == activeProfileId) {
            onProfileChanged(profile)
            return
        }
        val current = profiles.firstOrNull { it.id == activeProfileId }
        val requireParentExit = current?.kidsMode == true && safety.load(activeProfileId).kids.requirePinForProfileExit
        if (requireParentExit) {
            if (parentPin.hasPin()) parentGateTarget = profile else missingParentPinTarget = profile
        } else {
            completeSwitch(profile)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 22.dp),
    ) {
        AstraWavePageHeader(
            title = if (launchMode) "Who's watching?" else "Profiles & Household",
            subtitle = "Separate watch history, recommendations, saved lists and kids settings for every person in the household.",
        )
        Spacer(Modifier.height(16.dp))
        if (!launchMode) {
            AstraWaveSecondaryButton(label = "← Back", onClick = onBack)
            Spacer(Modifier.height(18.dp))
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            profiles.forEach { profile ->
                AstraWaveFocusableCard(Modifier.width(190.dp).clickable { choose(profile) }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(82.dp).background(
                                if (profile.id == activeProfileId) AstraWaveColors.SurfaceFocus else AstraWaveColors.SurfaceRaised,
                                CircleShape,
                            ),
                            contentAlignment = Alignment.Center,
                        ) { Text(profile.avatar, style = MaterialTheme.typography.headlineLarge) }
                        Spacer(Modifier.height(12.dp))
                        Text(profile.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            when {
                                profile.kidsMode && profile.pinProtected -> "KIDS • PIN"
                                profile.kidsMode -> "KIDS"
                                profile.pinProtected -> "PIN PROTECTED"
                                else -> "ADULT"
                            },
                            color = if (profile.kidsMode) AstraWaveColors.Success else AstraWaveColors.SecondaryText,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (profile.id == activeProfileId) {
                            Spacer(Modifier.height(5.dp))
                            Text("ACTIVE", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelMedium)
                        }
                        if (!launchMode) {
                            Spacer(Modifier.height(12.dp))
                            AstraWaveSecondaryButton(label = "Edit", onClick = { editing = profile })
                        }
                    }
                }
            }

            if (profiles.size < HouseholdProfileStore.MAX_PROFILES && !launchMode) {
                AstraWaveFocusableCard(Modifier.width(190.dp).clickable { createProfile = true }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(82.dp).background(AstraWaveColors.SurfaceRaised, CircleShape), contentAlignment = Alignment.Center) {
                            Text("＋", color = AstraWaveColors.Accent, style = MaterialTheme.typography.headlineLarge)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Add Profile", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text("Up to ${HouseholdProfileStore.MAX_PROFILES}", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (!launchMode) {
            Spacer(Modifier.height(28.dp))
            AstraWaveSectionHeader(
                title = "Household controls",
                subtitle = "Profiles use independent local libraries and recommendation feedback automatically.",
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = promptAtLaunch,
                    onCheckedChange = {
                        promptAtLaunch = it
                        store.setPromptAtLaunch(it)
                    },
                )
                Text("Ask who is watching when AstraWave starts", color = AstraWaveColors.PrimaryText)
            }
            Spacer(Modifier.height(10.dp))
            AstraWaveStatePanel(
                title = "Kids exit protection",
                message = if (parentPin.hasPin()) {
                    "A household Parent PIN protects exits from Kids profiles when that profile requires PIN protection. Adult profile PINs can still add a second lock."
                } else {
                    "Set a household Parent PIN in Parental Controls to prevent a Kids profile from switching into an unprotected adult profile."
                },
            )
        }
    }

    if (createProfile) {
        ProfileEditorDialog(
            initial = null,
            onDismiss = { createProfile = false },
            onSave = { name, avatar, kids, pin, _ ->
                val created = store.addProfile(name, avatar, kids, pin)
                if (created != null) {
                    runCatching { com.astrawave.app.data.FirebaseCloudRepository(context).saveProfile(created.id, created.name, created.kidsMode) }
                    refresh()
                    createProfile = false
                }
            },
        )
    }

    editing?.let { profile ->
        ProfileEditorDialog(
            initial = profile,
            onDismiss = { editing = null },
            onDelete = if (profiles.size > 1) ({ deleteTarget = profile; editing = null }) else null,
            onSave = { name, avatar, kids, pin, clearPin ->
                val updated = store.updateProfile(profile.id, name, avatar, kids, pin, clearPin)
                if (updated != null) {
                    runCatching { com.astrawave.app.data.FirebaseCloudRepository(context).saveProfile(updated.id, updated.name, updated.kidsMode) }
                    refresh()
                    editing = null
                    if (updated.id == activeProfileId) onProfileChanged(updated)
                }
            },
        )
    }

    parentGateTarget?.let { profile ->
        ParentPinDialog(
            onDismiss = { parentGateTarget = null },
            verify = parentPin::verify,
            onVerified = {
                parentGateTarget = null
                completeSwitch(profile)
            },
        )
    }

    missingParentPinTarget?.let {
        AlertDialog(
            onDismissRequest = { missingParentPinTarget = null },
            title = { Text("Parent PIN required") },
            text = { Text("This Kids profile is protected from profile exit, but no household Parent PIN has been set. Open Parental Controls from My AstraWave and create a Parent PIN first.") },
            confirmButton = { TextButton(onClick = { missingParentPinTarget = null }) { Text("OK") } },
        )
    }

    pinTarget?.let { profile ->
        PinDialog(
            profile = profile,
            onDismiss = { pinTarget = null },
            onVerified = {
                store.setActive(profile.id)
                pinTarget = null
                onProfileChanged(profile)
            },
            verify = { pin -> store.verifyPin(profile.id, pin) },
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${profile.name}?") },
            text = { Text("This removes the household profile entry. Existing local viewing data for its profile ID is left intact so accidental deletion is recoverable in a future restore flow.") },
            confirmButton = {
                TextButton(onClick = {
                    store.deleteProfile(profile.id)
                    refresh()
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProfileEditorDialog(
    initial: HouseholdProfileStore.Profile?,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean, String?, Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var avatar by remember(initial?.id) { mutableStateOf(initial?.avatar ?: "👤") }
    var kidsMode by remember(initial?.id) { mutableStateOf(initial?.kidsMode ?: false) }
    var pin by remember(initial?.id) { mutableStateOf("") }
    var clearPin by remember(initial?.id) { mutableStateOf(false) }
    val avatars = listOf("👤", "😎", "🚀", "🎬", "🎮", "🦊", "🐼", "🦄", "⚽", "⭐")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Profile" else "Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text("Profile name") }, singleLine = true)
                Text("Avatar", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    avatars.forEach { choice ->
                        Text(
                            choice,
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.background(if (avatar == choice) AstraWaveColors.SurfaceFocus else AstraWaveColors.SurfaceRaised, CircleShape)
                                .clickable { avatar = choice }.padding(10.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = kidsMode, onCheckedChange = { kidsMode = it })
                    Text("Kids profile")
                }
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> pin = value.filter(Char::isDigit).take(6) },
                    label = { Text(if (initial?.pinProtected == true) "New profile PIN (leave blank to keep current)" else "Optional profile PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (initial?.pinProtected == true) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = clearPin, onCheckedChange = { clearPin = it })
                        Text("Remove profile PIN protection")
                    }
                }
                onDelete?.let { TextButton(onClick = it) { Text("Delete profile", color = AstraWaveColors.Error) } }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name, avatar, kidsMode, pin.takeIf { it.isNotBlank() }, clearPin) }) {
                Text(if (initial == null) "Add" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ParentPinDialog(
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    verify: (String) -> Boolean,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Parent PIN") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6); error = false },
                    label = { Text("Parent PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text("Incorrect Parent PIN", color = AstraWaveColors.Error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (verify(pin)) onVerified() else error = true }) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PinDialog(
    profile: HouseholdProfileStore.Profile,
    onDismiss: () -> Unit,
    onVerified: () -> Unit,
    verify: (String) -> Boolean,
) {
    var pin by remember(profile.id) { mutableStateOf("") }
    var error by remember(profile.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter PIN for ${profile.name}") },
        text = {
            Column {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6); error = false },
                    label = { Text("Profile PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text("Incorrect PIN", color = AstraWaveColors.Error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (verify(pin)) onVerified() else error = true }) { Text("Unlock") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
