package com.astrawave.app.ui

import android.widget.Toast
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.astrawave.app.core.AccountOverview
import com.astrawave.app.core.AccountSection
import com.astrawave.app.core.AstraWaveList
import com.astrawave.app.core.OnboardingStep
import com.astrawave.app.data.LibraryCloudSync
import com.astrawave.app.data.LocalLibraryStore

/** Local-first My AstraWave hub. Firebase restores into the same local models when signed in. */
@Composable
fun MyAstraWaveHub(
    account: AccountOverview,
    lists: List<AstraWaveList> = emptyList(),
    onOpenAccountSection: (AccountSection) -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { LocalLibraryStore(context) }
    val cloudSync = remember { LibraryCloudSync(context) }
    val profileId = account.activeProfileId
    var snapshot by remember(profileId) { mutableStateOf(store.snapshot(profileId)) }
    var activeView by remember { mutableStateOf<PersonalLibraryView?>(null) }
    var createList by remember { mutableStateOf(false) }
    var showSetup by remember { mutableStateOf(false) }
    var showSafety by remember { mutableStateOf(false) }
    var showSubscription by remember { mutableStateOf(false) }
    var showExperienceSettings by remember { mutableStateOf(false) }

    fun refresh() { snapshot = store.snapshot(profileId) }

    LaunchedEffect(profileId) {
        cloudSync.restore(profileId) { result ->
            if (result.isSuccess) refresh()
        }
    }

    if (showExperienceSettings) {
        AstraWaveExperienceSettingsScreen(profileId = profileId, onBack = { showExperienceSettings = false })
        return
    }

    if (showSafety) {
        SafetySettingsScreen(profileId = profileId, onBack = { showSafety = false })
        return
    }

    if (showSubscription) {
        SubscriptionOverviewScreen(
            currentPlanName = account.planName,
            onBack = { showSubscription = false },
        )
        return
    }

    if (showSetup) {
        AstraWaveOnboardingScreen(
            profileId = profileId,
            onOpenStep = { step ->
                when (step) {
                    OnboardingStep.PROFILE -> onOpenAccountSection(AccountSection.PROFILES)
                    OnboardingStep.LIVE_TV -> onOpenAccountSection(AccountSection.IPTV)
                    OnboardingStep.ADDONS -> onOpenAccountSection(AccountSection.ADDONS)
                    OnboardingStep.PERSONAL_MEDIA -> onOpenAccountSection(AccountSection.PERSONAL_MEDIA)
                    OnboardingStep.DEVICE_PAIRING -> onOpenAccountSection(AccountSection.DEVICES)
                    OnboardingStep.PRIVACY -> showSafety = true
                    OnboardingStep.TMDB,
                    OnboardingStep.AUDIO,
                    OnboardingStep.WELCOME,
                    OnboardingStep.COMPLETE,
                    -> Unit
                }
            },
            onFinished = { showSetup = false },
        )
        return
    }

    val selected = activeView
    if (selected != null) {
        when (selected) {
            PersonalLibraryView.Watchlist -> PersonalLibraryScreen("Watchlist", watchlistItems(snapshot.watchlist), { activeView = null })
            PersonalLibraryView.Favorites -> PersonalLibraryScreen("Favorites", favoriteItems(snapshot.favorites), { activeView = null })
            PersonalLibraryView.History -> PersonalLibraryScreen("History", historyItems(snapshot.history), { activeView = null })
            PersonalLibraryView.ContinueWatching -> ContinueWatchingScreen(store.continueWatching(profileId), { activeView = null })
            is PersonalLibraryView.CustomList -> {
                val list = snapshot.lists.firstOrNull { it.id == selected.listId }
                PersonalLibraryScreen(list?.name ?: "My List", list?.items.orEmpty(), { activeView = null })
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(bottom = 36.dp),
    ) {
        AccountHeader(account)

        Text("MY LIBRARY", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LibraryShortcut("Watchlist", "${snapshot.watchlist.size} items", Icons.Default.VideoLibrary) { activeView = PersonalLibraryView.Watchlist }
            LibraryShortcut("Favorites", "${snapshot.favorites.size} items", Icons.Default.Favorite) { activeView = PersonalLibraryView.Favorites }
            LibraryShortcut("Continue", "${store.continueWatching(profileId).size} in progress", Icons.Default.PlayCircle) { activeView = PersonalLibraryView.ContinueWatching }
            LibraryShortcut("History", "${snapshot.history.size} played", Icons.Default.History) { activeView = PersonalLibraryView.History }
        }

        Spacer(Modifier.height(28.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("My Lists", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Row(
                Modifier.background(AstraWaveColors.SurfaceRaised, RoundedCornerShape(12.dp))
                    .clickable { createList = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Add, null, tint = AstraWaveColors.Accent, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("New list", color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(10.dp))
        if (snapshot.lists.isEmpty()) {
            EmptyListsCard { createList = true }
        } else {
            snapshot.lists.sortedWith(compareByDescending<AstraWaveList> { it.isPinned }.thenBy { it.sortOrder }).forEach { list ->
                ListRow(list) { activeView = PersonalLibraryView.CustomList(list.id) }
            }
        }

        Spacer(Modifier.height(26.dp))
        Text("ACCOUNT & SETTINGS", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
        SetupRow { showSetup = true }
        ExperienceSettingsRow { showExperienceSettings = true }
        AccountSection.entries.forEach { section ->
            AccountRow(section) {
                when (section) {
                    AccountSection.SUBSCRIPTION -> showSubscription = true
                    AccountSection.PARENTAL_CONTROLS, AccountSection.PRIVACY -> showSafety = true
                    else -> onOpenAccountSection(section)
                }
            }
        }
    }

    if (createList) {
        CreateListDialog(
            profileId = profileId,
            onDismiss = { createList = false },
            onCreate = { list ->
                store.saveList(list)
                refresh()
                createList = false
            },
        )
    }
}

@Composable
private fun AstraWaveExperienceSettingsScreen(profileId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("astrawave_experience", 0) }
    fun key(name: String) = "$profileId:$name"
    var theme by remember(profileId) { mutableStateOf(prefs.getString(key("theme"), "AstraWave") ?: "AstraWave") }
    var density by remember(profileId) { mutableStateOf(prefs.getString(key("density"), "Standard") ?: "Standard") }
    var quality by remember(profileId) { mutableStateOf(prefs.getString(key("quality"), "Auto") ?: "Auto") }
    var subtitleLanguage by remember(profileId) { mutableStateOf(prefs.getString(key("subtitleLanguage"), "English") ?: "English") }
    var guideDays by remember(profileId) { mutableStateOf(prefs.getInt(key("guideDays"), 7)) }
    var reducedMotion by remember(profileId) { mutableStateOf(prefs.getBoolean(key("reducedMotion"), false)) }
    var autoplayBest by remember(profileId) { mutableStateOf(prefs.getBoolean(key("autoplayBest"), true)) }

    fun saveString(name: String, value: String) { prefs.edit().putString(key(name), value).apply() }
    fun saveInt(name: String, value: Int) { prefs.edit().putInt(key(name), value).apply() }
    fun saveBoolean(name: String, value: Boolean) { prefs.edit().putBoolean(key(name), value).apply() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(AstraWaveColors.Background).padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Experience & Parity Settings", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            Text("Back", color = AstraWaveColors.Accent, modifier = Modifier.clickable(onClick = onBack).padding(10.dp))
        }
        Text("These profile-scoped preferences keep the Android APK aligned with the web experience while preserving native Android TV behavior.", color = AstraWaveColors.SecondaryText)

        ExperienceChoice("Theme", listOf("AstraWave", "OLED Black", "Blue Cinema"), theme) { value -> theme = value; saveString("theme", value) }
        ExperienceChoice("Density", listOf("Compact", "Standard", "Cinematic"), density) { value -> density = value; saveString("density", value) }
        ExperienceChoice("Preferred quality", listOf("Auto", "Best", "4K", "1080p", "720p", "Data Saver"), quality) { value -> quality = value; saveString("quality", value) }
        ExperienceChoice("Subtitle language", listOf("English", "Spanish", "French", "German", "Portuguese", "Off"), subtitleLanguage) { value -> subtitleLanguage = value; saveString("subtitleLanguage", value) }
        ExperienceChoice("Guide horizon", listOf("1 day", "3 days", "7 days"), "$guideDays days") { value ->
            guideDays = when (value) { "1 day" -> 1; "3 days" -> 3; else -> 7 }
            saveInt("guideDays", guideDays)
        }

        ExperienceToggle("Autoplay best healthy source", "Prefer AstraWave's highest-ranked verified source when available.", autoplayBest) { value -> autoplayBest = value; saveBoolean("autoplayBest", value) }
        ExperienceToggle("Reduced motion", "Minimize decorative motion for accessibility and TV comfort.", reducedMotion) { value -> reducedMotion = value; saveBoolean("reducedMotion", value) }

        AstraWaveFocusableCard(Modifier.fillMaxWidth()) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, null, tint = AstraWaveColors.Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("APK ↔ Web parity", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                Text("Current shared feature families: profiles & Kids, expanded VOD/discovery, title/franchise context, watch progress, sports, Guide, Multiview, source management, personal media, player controls, onboarding, appearance preferences, and diagnostics.", color = AstraWaveColors.SecondaryText)
                Spacer(Modifier.height(10.dp))
                Text("Android-only capabilities remain native where appropriate, including TV remote focus, Media3 playback, secure personal-media credentials, and device-native PiP.", color = AstraWaveColors.TertiaryText)
            }
        }

        AstraWaveFocusableCard(Modifier.fillMaxWidth().clickable {
            Toast.makeText(context, "Experience settings saved for this profile.", Toast.LENGTH_SHORT).show()
        }) {
            Text("Save / confirm profile settings", color = AstraWaveColors.Accent, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ExperienceChoice(title: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val isSelected = selected.equals(option, ignoreCase = true) || (title == "Guide horizon" && selected.startsWith(option.substringBefore(' ')))
                FilterChip(selected = isSelected, onClick = { onSelect(option) }, label = { Text(option) })
            }
        }
    }
}

@Composable
private fun ExperienceToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(AstraWaveColors.Surface, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AccountHeader(account: AccountOverview) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 24.dp)
            .background(AstraWaveColors.Surface, RoundedCornerShape(22.dp)).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).background(AstraWaveColors.SurfaceFocus, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.AccountCircle, null, tint = AstraWaveColors.AccentStrong, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(account.displayName, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge)
            Text(account.planName, color = AstraWaveColors.Accent, style = MaterialTheme.typography.labelLarge)
            account.email?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (account.cloudSyncEnabled) Icon(Icons.Default.CloudDone, "Cloud sync enabled", tint = AstraWaveColors.Success)
    }
}

@Composable
private fun LibraryShortcut(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    AstraWaveFocusableCard(Modifier.width(150.dp).clickable(onClick = onClick)) {
        Column {
            Icon(icon, null, tint = AstraWaveColors.Accent, modifier = Modifier.size(25.dp))
            Spacer(Modifier.height(18.dp))
            Text(title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun ListRow(list: AstraWaveList, onClick: () -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 5.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (list.isPinned) Icons.Default.Star else Icons.Default.List, null,
                tint = if (list.isPinned) AstraWaveColors.Warning else AstraWaveColors.Accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(list.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                Text("${list.items.size} items${if (list.description.isNotBlank()) " • ${list.description}" else ""}",
                    color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Default.ChevronRight, null, tint = AstraWaveColors.TertiaryText)
        }
    }
}

@Composable
private fun EmptyListsCard(onCreate: () -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(horizontal = 22.dp).clickable(onClick = onCreate)) {
        Column {
            Text("Create your first list", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text("Build any collection you want. Lists are stored locally and remain available offline.",
                color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SetupRow(onClick: () -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, null, tint = AstraWaveColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Setup & Onboarding", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyLarge)
                Text("Resume or review AstraWave setup", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.Default.ChevronRight, null, tint = AstraWaveColors.TertiaryText)
        }
    }
}

@Composable
private fun ExperienceSettingsRow(onClick: () -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Tune, null, tint = AstraWaveColors.Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Experience & Web Parity", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyLarge)
                Text("Theme, density, playback, subtitles and Guide preferences", color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.Default.ChevronRight, null, tint = AstraWaveColors.TertiaryText)
        }
    }
}

@Composable
private fun AccountRow(section: AccountSection, onClick: () -> Unit) {
    AstraWaveFocusableCard(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp).clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Settings, null, tint = AstraWaveColors.SecondaryText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(section.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = AstraWaveColors.TertiaryText)
        }
    }
}
