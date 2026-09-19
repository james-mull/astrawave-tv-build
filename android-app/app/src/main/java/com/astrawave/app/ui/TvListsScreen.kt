package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.PremiumVodDetailActivity
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.data.BuiltInCatalogMediaType
import com.astrawave.app.data.DynamicCollectionRepository
import com.astrawave.app.data.HouseholdProfileStore
import com.astrawave.app.data.LocalLibraryStore

/** TV Shows entry point; kids profiles keep the restricted surface. */
@Composable
fun TvListsScreen(profileId: String = "default") {
    val context = LocalContext.current
    val device = LocalAstraWaveDeviceClass.current
    val household = remember { HouseholdProfileStore(context) }
    val isKids = household.profiles().firstOrNull { it.id == profileId }?.kidsMode == true
    if (isKids) {
        KidsDiscoveryScreen(profileId = profileId, media = DynamicCollectionRepository.Media.SERIES)
        return
    }

    val library = remember { LocalLibraryStore(context) }
    val continueTv = remember(profileId) {
        library.continueWatching(profileId)
            .filter { progress -> progress.item.type == LibraryMediaType.EPISODE || progress.item.type == LibraryMediaType.SERIES }
            .distinctBy { it.item.id }
            .take(14)
    }

    fun open(progress: LocalLibraryStore.PlaybackProgress) {
        val item = progress.item
        val target = if (item.type == LibraryMediaType.EPISODE) TitleDetailsActivity::class.java else PremiumVodDetailActivity::class.java
        context.startActivity(
            Intent(context, target)
                .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.title)
                .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, item.type.name)
                .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, item.sourceId)
                .putExtra(TitleDetailsActivity.EXTRA_PROFILE_ID, profileId),
        )
    }

    Column(Modifier.fillMaxSize().background(AstraWaveColors.Background)) {
        continueTv.firstOrNull()?.let { featured ->
            val ratio = if (featured.durationMs > 0L) (featured.positionMs.toFloat() / featured.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
            Box(
                Modifier.fillMaxWidth()
                    .height(if (device == AstraWaveDeviceClass.PHONE) 320.dp else 380.dp)
                    .clickable { open(featured) },
            ) {
                AstraWaveArtwork(featured.item.title, Modifier.fillMaxSize(), AstraWaveArtworkKind.Backdrop)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                AstraWaveColors.Background.copy(alpha = 0.05f),
                                AstraWaveColors.Background.copy(alpha = 0.28f),
                                AstraWaveColors.Background,
                            ),
                        ),
                    ),
                )
                Column(
                    Modifier.align(Alignment.BottomStart)
                        .padding(horizontal = if (device == AstraWaveDeviceClass.PHONE) 16.dp else 28.dp, vertical = 24.dp)
                        .width(if (device == AstraWaveDeviceClass.PHONE) 330.dp else 620.dp),
                ) {
                    Text("CONTINUE SERIES", color = AstraWaveColors.AccentStrong, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(featured.item.title, color = AstraWaveColors.PrimaryText, style = if (device == AstraWaveDeviceClass.PHONE) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayLarge, maxLines = 2)
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(3.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("RESUME  ▶", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (continueTv.size > 1) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
                Text("Continue Watching", color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = if (device == AstraWaveDeviceClass.PHONE) 16.dp else 22.dp))
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = if (device == AstraWaveDeviceClass.PHONE) 16.dp else 22.dp)) {
                    items(continueTv.drop(1), key = { it.item.id }) { progress ->
                        Column(Modifier.width(if (device == AstraWaveDeviceClass.PHONE) 210.dp else 250.dp).clickable { open(progress) }) {
                            AstraWaveArtwork(progress.item.title, Modifier.fillMaxWidth(), AstraWaveArtworkKind.Backdrop)
                            Spacer(Modifier.height(6.dp))
                            Text(progress.item.title, color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                        }
                    }
                }
            }
        }

        Column(Modifier.weight(1f)) {
            BuiltInCatalogHubScreen(profileId = profileId, mediaType = BuiltInCatalogMediaType.SHOW)
        }
    }
}
