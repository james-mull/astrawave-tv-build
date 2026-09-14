package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PremiumVodDetailActivity
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.BuiltInCatalogMediaType
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.LocalLibraryStore

/**
 * Movies entry point. Kids profiles retain the restricted discovery surface; adult profiles
 * use AstraWave's built-in movie catalogs with a profile-aware Continue Watching rail.
 */
@Composable
fun MovieListsScreen(profileId: String = "default") {
    val context = LocalContext.current
    val household = remember { HouseholdProfileStore(context) }
    val isKids = household.profiles().firstOrNull { it.id == profileId }?.kidsMode == true
    if (isKids) {
        KidsDiscoveryScreen(profileId = profileId, media = DynamicCollectionRepository.Media.MOVIE)
        return
    }

    val library = remember { LocalLibraryStore(context) }
    val continueMovies = remember(profileId) {
        library.continueWatching(profileId)
            .filter { progress -> progress.item.type == LibraryMediaType.MOVIE }
            .distinctBy { it.item.id }
            .take(14)
    }

    fun open(progress: LocalLibraryStore.PlaybackProgress) {
        val item = progress.item
        context.startActivity(
            Intent(context, PremiumVodDetailActivity::class.java)
                .putExtra(PremiumVodDetailActivity.EXTRA_TITLE, item.title)
                .putExtra(PremiumVodDetailActivity.EXTRA_MEDIA_TYPE, item.type.name)
                .putExtra(PremiumVodDetailActivity.EXTRA_SOURCE_ID, item.sourceId)
                .putExtra(PremiumVodDetailActivity.EXTRA_PROFILE_ID, profileId),
        )
    }

    Column(Modifier.fillMaxSize()) {
        if (continueMovies.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp)) {
                Text(
                    "Continue Watching",
                    color = AstraWaveColors.PrimaryText,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 22.dp),
                )
                Spacer(Modifier.height(9.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp),
                ) {
                    items(continueMovies, key = { it.item.id }) { progress ->
                        val ratio = if (progress.durationMs > 0L) {
                            (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        AstraWaveFocusableCard(
                            Modifier.width(280.dp).clickable { open(progress) },
                        ) {
                            Column {
                                AstraWaveArtwork(
                                    progress.item.title,
                                    Modifier.fillMaxWidth(),
                                    AstraWaveArtworkKind.Backdrop,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    progress.item.title,
                                    color = AstraWaveColors.PrimaryText,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.height(6.dp))
                                LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                                Spacer(Modifier.height(5.dp))
                                Text(
                                    if (ratio > 0f) "Resume • ${(ratio * 100).toInt()}%" else "Continue movie",
                                    color = AstraWaveColors.AccentStrong,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }

        Column(Modifier.weight(1f)) {
            BuiltInCatalogHubScreen(profileId = profileId, mediaType = BuiltInCatalogMediaType.MOVIE)
        }
    }
}
