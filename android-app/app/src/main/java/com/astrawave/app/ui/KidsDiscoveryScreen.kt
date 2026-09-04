package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.ArtworkRegistry
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.KidsModePolicyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface KidsRowState {
    data object Loading : KidsRowState
    data class Ready(val items: List<AstraWaveMetadataGateway.Item>) : KidsRowState
    data class Error(val message: String) : KidsRowState
}

@Composable
fun KidsDiscoveryScreen(
    profileId: String,
    media: DynamicCollectionRepository.Media,
) {
    val context = LocalContext.current
    val policyStore = remember { KidsModePolicyStore(context) }
    val policy = remember(profileId) { policyStore.load(profileId) }
    val repository = remember { DynamicCollectionRepository() }
    val genres = remember(profileId) { policyStore.allowedGenres(profileId) }
    val states = remember(profileId, media) { mutableStateMapOf<String, KidsRowState>() }

    if (policyStore.bedtimeActive(profileId)) {
        Column(
            Modifier.fillMaxSize().background(AstraWaveColors.Background).padding(24.dp),
        ) {
            AstraWavePageHeader(
                title = "Kids Mode",
                subtitle = "Bedtime is active for this profile.",
            )
            Spacer(Modifier.height(18.dp))
            AstraWaveStatePanel(
                title = "Time for a break",
                message = "Kids viewing is paused during the parent-set bedtime window. Switch to an adult profile to change this schedule.",
            )
        }
        return
    }

    LaunchedEffect(profileId, media, genres) {
        genres.forEach { genre ->
            states[genre] = KidsRowState.Loading
            states[genre] = try {
                val items = withContext(Dispatchers.IO) {
                    repository.genre(media, genre, pages = if (policy.approvedOnly) 1 else 2)
                        .distinctBy { it.id }
                        .take(if (policy.approvedOnly) 24 else 40)
                        .onEach { ArtworkRegistry.register(it.name, it.posterUrl ?: it.backdropUrl) }
                }
                KidsRowState.Ready(items)
            } catch (error: Exception) {
                KidsRowState.Error(error.message ?: "Unable to load kids collection")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .background(AstraWaveColors.Background).padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        AstraWavePageHeader(
            title = if (media == DynamicCollectionRepository.Media.MOVIE) "Kids Movies" else "Kids TV",
            subtitle = "${policy.ageLevel.label} • ${if (policy.approvedOnly) "Approved-only discovery" else "Family-safe discovery"}",
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "This profile uses a restricted discovery surface. External addon catalogs, adult genre charts and unrestricted search are excluded here.",
            color = AstraWaveColors.SecondaryText,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(22.dp))

        genres.forEach { genre ->
            val state = states[genre] ?: KidsRowState.Loading
            Text(genre, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(9.dp))
            when (state) {
                KidsRowState.Loading -> AstraWaveStatePanel("Loading $genre…", "Refreshing this kids collection.", loading = true)
                is KidsRowState.Error -> AstraWaveStatePanel("$genre unavailable", state.message)
                is KidsRowState.Ready -> KidsMediaRow(
                    items = state.items,
                    mediaType = if (media == DynamicCollectionRepository.Media.MOVIE) LibraryMediaType.MOVIE else LibraryMediaType.SERIES,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun KidsMediaRow(
    items: List<AstraWaveMetadataGateway.Item>,
    mediaType: LibraryMediaType,
) {
    val context = LocalContext.current
    if (items.isEmpty()) {
        AstraWaveStatePanel("Nothing to show", "No matching kids titles are available in this collection right now.")
        return
    }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.forEach { item ->
            AstraWaveFocusableCard(
                Modifier.width(220.dp).clickable {
                    val sourceId = if (item.id.startsWith("tt", true)) {
                        "stremio:cinemeta:${if (mediaType == LibraryMediaType.MOVIE) "movie" else "series"}:${item.id}"
                    } else "tmdb:${item.id}"
                    context.startActivity(
                        Intent(context, TitleDetailsActivity::class.java)
                            .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.name)
                            .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, mediaType.name)
                            .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, sourceId),
                    )
                },
            ) {
                Column {
                    Box(Modifier.fillMaxWidth().height(130.dp)) {
                        AstraWaveArtwork(title = item.name, modifier = Modifier.fillMaxSize())
                        Text(
                            "KIDS",
                            color = AstraWaveColors.PrimaryText,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.TopStart).padding(7.dp)
                                .background(AstraWaveColors.Background.copy(alpha = 0.82f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                        )
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(item.name, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                    item.releaseInfo?.let {
                        Spacer(Modifier.height(3.dp))
                        Text(it, color = AstraWaveColors.SecondaryText, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}
