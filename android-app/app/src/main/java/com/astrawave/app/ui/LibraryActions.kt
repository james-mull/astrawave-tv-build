package com.astrawave.app.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.astrawave.app.TitleDetailsActivity
import com.astrawave.app.core.FavoriteEntry
import com.astrawave.app.core.LibraryItemRef
import com.astrawave.app.core.LibraryMediaType
import com.astrawave.app.core.StremioMetaItem
import com.astrawave.app.core.WatchlistEntry
import com.astrawave.app.data.FirebaseCloudRepository
import com.astrawave.app.data.LocalLibraryStore
import com.astrawave.app.data.TmdbItem

fun TmdbItem.toLibraryItemRef(): LibraryItemRef = LibraryItemRef(
    id = "tmdb:${mediaType ?: "unknown"}:$id",
    type = when (mediaType) {
        "tv" -> LibraryMediaType.SERIES
        else -> LibraryMediaType.MOVIE
    },
    title = title,
    posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
    sourceId = "tmdb:$id",
)

fun StremioMetaItem.toLibraryItemRef(addonId: String): LibraryItemRef = LibraryItemRef(
    id = "stremio:$addonId:$type:$id",
    type = when (type.lowercase()) {
        "series", "tv", "show" -> LibraryMediaType.SERIES
        "episode" -> LibraryMediaType.EPISODE
        else -> LibraryMediaType.MOVIE
    },
    title = name,
    posterUrl = posterUrl,
    sourceId = "stremio:$addonId:$type:$id",
)

@Composable
fun LibraryActionRow(
    item: LibraryItemRef,
    profileId: String = "default",
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { LocalLibraryStore(context) }
    val cloud = remember { FirebaseCloudRepository(context) }
    var inWatchlist by remember(item.id, profileId) {
        mutableStateOf(store.watchlist(profileId).any { it.item.id == item.id })
    }
    var favorite by remember(item.id, profileId) {
        mutableStateOf(store.favorites(profileId).any { it.item.id == item.id })
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AstraWaveFocusableCard(
            Modifier.clickable {
                context.startActivity(
                    Intent(context, TitleDetailsActivity::class.java)
                        .putExtra(TitleDetailsActivity.EXTRA_TITLE, item.title)
                        .putExtra(TitleDetailsActivity.EXTRA_MEDIA_TYPE, item.type.name)
                        .putExtra(TitleDetailsActivity.EXTRA_SOURCE_ID, item.sourceId),
                )
            },
        ) {
            Text(
                "Open",
                color = AstraWaveColors.Accent,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        AstraWaveFocusableCard(
            Modifier.clickable {
                inWatchlist = !inWatchlist
                val entry = WatchlistEntry(profileId = profileId, item = item)
                store.setWatchlist(entry, inWatchlist)
                if (cloud.signedIn) {
                    if (inWatchlist) cloud.saveWatchlist(entry) else cloud.removeWatchlist(profileId, item.id)
                }
            },
        ) {
            Text(
                if (inWatchlist) "✓ Watchlist" else "+ Watchlist",
                color = if (inWatchlist) AstraWaveColors.Success else AstraWaveColors.PrimaryText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        AstraWaveFocusableCard(
            Modifier.clickable {
                favorite = !favorite
                val entry = FavoriteEntry(profileId = profileId, item = item)
                store.setFavorite(entry, favorite)
                if (cloud.signedIn) {
                    if (favorite) cloud.saveFavorite(entry) else cloud.removeFavorite(profileId, item.id)
                }
            },
        ) {
            Text(
                if (favorite) "♥ Favorite" else "♡ Favorite",
                color = if (favorite) AstraWaveColors.Success else AstraWaveColors.PrimaryText,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}
