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
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

    fun refresh() { snapshot = store.snapshot(profileId) }

    LaunchedEffect(profileId) {
        cloudSync.restore(profileId) { result ->
            if (result.isSuccess) refresh()
        }
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
        AccountSection.entries.forEach { section -> AccountRow(section) { onOpenAccountSection(section) } }
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
