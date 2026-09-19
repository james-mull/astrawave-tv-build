package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.astrawave.app.MovieListDetailActivity
import com.astrawave.app.PremiumVodDetailActivity
import com.astrawave.app.TvListDetailActivity
import com.astrawave.app.data.AstraWaveMetadataGateway
import com.astrawave.app.data.BuiltInCatalogDefinition
import com.astrawave.app.data.BuiltInCatalogMediaType
import com.astrawave.app.data.BuiltInCatalogRepository
import com.astrawave.app.data.CatalogServiceOverrides
import com.astrawave.app.data.VerifiedMdbListCatalogs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CatalogPreviewShelf(
    section: String,
    definitions: List<BuiltInCatalogDefinition>,
    profileId: String,
) {
    val lead = remember(section, definitions) {
        when (section) {
            "Streaming Services" -> definitions.firstOrNull { it.id in CatalogServiceOverrides }
            else -> definitions.firstOrNull { it.id in VerifiedMdbListCatalogs }
        } ?: definitions.firstOrNull { it.featured }
            ?: definitions.firstOrNull()
    } ?: return
    CatalogContentShelf(lead, profileId)
}

@Composable
fun CatalogContentShelf(
    definition: BuiltInCatalogDefinition,
    profileId: String,
) {
    val context = LocalContext.current
    val device = LocalAstraWaveDeviceClass.current
    val repository = remember { BuiltInCatalogRepository(context) }
    var items by remember(definition.id, profileId) { mutableStateOf<List<AstraWaveMetadataGateway.Item>>(emptyList()) }

    LaunchedEffect(definition.id, profileId) {
        val result = withContext(Dispatchers.IO) {
            runCatching { repository.page(definition.id, profileId, offset = 0, pageSize = 18) }.getOrNull()
        }
        items = result?.items.orEmpty()
    }

    if (items.isEmpty()) return

    fun openCatalog() {
        val intent = if (definition.mediaType == BuiltInCatalogMediaType.MOVIE) {
            Intent(context, MovieListDetailActivity::class.java)
                .putExtra(MovieListDetailActivity.EXTRA_CATALOG_ID, definition.id)
                .putExtra(MovieListDetailActivity.EXTRA_TITLE, definition.title)
                .putExtra(MovieListDetailActivity.EXTRA_PROFILE_ID, profileId)
        } else {
            Intent(context, TvListDetailActivity::class.java)
                .putExtra(TvListDetailActivity.EXTRA_CATALOG_ID, definition.id)
                .putExtra(TvListDetailActivity.EXTRA_TITLE, definition.title)
                .putExtra(TvListDetailActivity.EXTRA_PROFILE_ID, profileId)
        }
        context.startActivity(intent)
    }

    fun openItem(item: AstraWaveMetadataGateway.Item) {
        val mediaType = if (
            definition.mediaType == BuiltInCatalogMediaType.SHOW ||
            item.type.equals("series", true) || item.type.equals("tv", true)
        ) "SERIES" else "MOVIE"
        val sourceId = when {
            item.id.startsWith("tt", true) -> "stremio:cinemeta:${if (mediaType == "SERIES") "series" else "movie"}:${item.id}"
            item.id.toLongOrNull() != null -> "tmdb:${item.id}"
            else -> null
        }
        context.startActivity(
            Intent(context, PremiumVodDetailActivity::class.java)
                .putExtra(PremiumVodDetailActivity.EXTRA_TITLE, item.name)
                .putExtra(PremiumVodDetailActivity.EXTRA_MEDIA_TYPE, mediaType)
                .putExtra(PremiumVodDetailActivity.EXTRA_SOURCE_ID, sourceId)
                .putExtra(PremiumVodDetailActivity.EXTRA_PROFILE_ID, profileId),
        )
    }

    val cardWidth = when (device) {
        AstraWaveDeviceClass.TV -> 172.dp
        AstraWaveDeviceClass.TABLET -> 154.dp
        AstraWaveDeviceClass.PHONE -> 138.dp
    }
    val posterHeight = when (device) {
        AstraWaveDeviceClass.TV -> 258.dp
        AstraWaveDeviceClass.TABLET -> 231.dp
        AstraWaveDeviceClass.PHONE -> 207.dp
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(end = if (device == AstraWaveDeviceClass.PHONE) 16.dp else 22.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                definition.title,
                color = AstraWaveColors.PrimaryText,
                style = if (device == AstraWaveDeviceClass.PHONE) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "View all  ›",
                color = AstraWaveColors.AccentStrong,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.clickable { openCatalog() }.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(if (device == AstraWaveDeviceClass.TV) 14.dp else 11.dp),
            contentPadding = PaddingValues(end = 34.dp, bottom = 5.dp),
        ) {
            items(items, key = { item -> "${definition.id}:${item.id}:${item.name}" }) { item ->
                val typeLabel = if (
                    definition.mediaType == BuiltInCatalogMediaType.SHOW ||
                    item.type.equals("series", true) || item.type.equals("tv", true)
                ) "TV" else "MOVIE"
                AstraWaveFocusableCard(
                    Modifier.width(cardWidth).clickable { openItem(item) },
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(posterHeight)
                                .clip(MaterialTheme.shapes.medium)
                                .background(AstraWaveColors.BackgroundRaised),
                            contentAlignment = Alignment.Center,
                        ) {
                            AsyncImage(
                                model = item.posterUrl ?: item.backdropUrl,
                                contentDescription = item.name,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Crop,
                            )
                            if (item.posterUrl.isNullOrBlank() && item.backdropUrl.isNullOrBlank()) {
                                Text(item.name.take(1), color = AstraWaveColors.PrimaryText, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                        Text(
                            item.name,
                            color = AstraWaveColors.PrimaryText,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.releaseInfo?.takeIf(String::isNotBlank)?.let { release ->
                            Text(
                                release.take(18),
                                color = AstraWaveColors.TertiaryText,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
