package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Movie-detail enrichment: AstraWave list memberships and TMDB collection/franchise parts. */
class MovieDetailContextRepository(
    private val apiBaseUrl: String = BuildConfig.ASTRAWAVE_API_BASE_URL.trimEnd('/'),
) {
    data class ListMembership(
        val title: String,
        val category: String,
        val reason: String,
    )

    data class CollectionMovie(
        val id: String,
        val title: String,
        val releaseDate: String? = null,
        val posterUrl: String? = null,
        val backdropUrl: String? = null,
    )

    data class MovieCollection(
        val id: String,
        val name: String,
        val posterUrl: String? = null,
        val backdropUrl: String? = null,
        val parts: List<CollectionMovie> = emptyList(),
    )

    data class Context(
        val lists: List<ListMembership> = emptyList(),
        val collection: MovieCollection? = null,
    )

    fun load(movieId: Long): Context {
        if (movieId <= 0L || apiBaseUrl.isBlank()) return Context()
        val encoded = URLEncoder.encode(movieId.toString(), StandardCharsets.UTF_8.name())
        val root = JSONObject(SimpleHttp.getText("$apiBaseUrl/movie-context/$encoded"))
        val listArray = root.optJSONArray("lists")
        val lists = buildList {
            if (listArray != null) {
                for (i in 0 until listArray.length()) {
                    val item = listArray.optJSONObject(i) ?: continue
                    val title = item.optString("title").takeIf { it.isNotBlank() } ?: continue
                    add(
                        ListMembership(
                            title = title,
                            category = item.optString("category").ifBlank { "AstraWave List" },
                            reason = item.optString("reason"),
                        ),
                    )
                }
            }
        }

        val collectionJson = root.optJSONObject("collection")
        val collection = collectionJson?.let { obj ->
            val partsJson = obj.optJSONArray("parts")
            val parts = buildList {
                if (partsJson != null) {
                    for (i in 0 until partsJson.length()) {
                        val part = partsJson.optJSONObject(i) ?: continue
                        val id = part.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val title = part.optString("title").takeIf { it.isNotBlank() } ?: continue
                        add(
                            CollectionMovie(
                                id = id,
                                title = title,
                                releaseDate = part.optString("releaseDate").takeIf { it.isNotBlank() && it != "null" },
                                posterUrl = part.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" },
                                backdropUrl = part.optString("backdropUrl").takeIf { it.isNotBlank() && it != "null" },
                            ),
                        )
                    }
                }
            }
            MovieCollection(
                id = obj.optString("id"),
                name = obj.optString("name").ifBlank { "Movie Collection" },
                posterUrl = obj.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" },
                backdropUrl = obj.optString("backdropUrl").takeIf { it.isNotBlank() && it != "null" },
                parts = parts,
            )
        }
        return Context(lists = lists, collection = collection)
    }
}
