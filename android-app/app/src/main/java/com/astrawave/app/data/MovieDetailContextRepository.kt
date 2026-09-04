package com.astrawave.app.data

import com.astrawave.app.BuildConfig
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/** Movie-detail enrichment: AstraWave list memberships, franchises, universes and related films. */
class MovieDetailContextRepository(
    private val apiBaseUrl: String = BuildConfig.ASTRAWAVE_API_BASE_URL.trimEnd('/'),
) {
    data class ListMembership(
        val id: String,
        val title: String,
        val category: String,
        val reason: String,
        val query: String? = null,
        val genre: String? = null,
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

    data class MovieUniverse(
        val name: String,
        val editorial: Boolean = true,
        val parts: List<CollectionMovie> = emptyList(),
    )

    data class PersonMovies(
        val id: String,
        val name: String,
        val movies: List<CollectionMovie> = emptyList(),
    )

    data class Related(
        val similar: List<CollectionMovie> = emptyList(),
        val director: PersonMovies? = null,
        val actor: PersonMovies? = null,
    )

    data class Context(
        val lists: List<ListMembership> = emptyList(),
        val collection: MovieCollection? = null,
        val universe: MovieUniverse? = null,
        val related: Related = Related(),
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
                            id = item.optString("id").ifBlank { title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-') },
                            title = title,
                            category = item.optString("category").ifBlank { "AstraWave List" },
                            reason = item.optString("reason"),
                            query = item.optString("query").takeIf { it.isNotBlank() && it != "null" },
                            genre = item.optString("genre").takeIf { it.isNotBlank() && it != "null" },
                        ),
                    )
                }
            }
        }

        fun parseMovies(parent: JSONObject?, key: String): List<CollectionMovie> {
            val array = parent?.optJSONArray(key) ?: return emptyList()
            return buildList {
                for (i in 0 until array.length()) {
                    val part = array.optJSONObject(i) ?: continue
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

        val collectionJson = root.optJSONObject("collection")
        val collection = collectionJson?.let { obj ->
            MovieCollection(
                id = obj.optString("id"),
                name = obj.optString("name").ifBlank { "Movie Collection" },
                posterUrl = obj.optString("posterUrl").takeIf { it.isNotBlank() && it != "null" },
                backdropUrl = obj.optString("backdropUrl").takeIf { it.isNotBlank() && it != "null" },
                parts = parseMovies(obj, "parts"),
            )
        }

        val universeJson = root.optJSONObject("universe")
        val universe = universeJson?.let { obj ->
            MovieUniverse(
                name = obj.optString("name").ifBlank { "Connected Universe" },
                editorial = obj.optBoolean("editorial", true),
                parts = parseMovies(obj, "parts"),
            )
        }

        val relatedJson = root.optJSONObject("related")
        fun parsePerson(key: String): PersonMovies? {
            val person = relatedJson?.optJSONObject(key) ?: return null
            val id = person.optString("id").takeIf { it.isNotBlank() } ?: return null
            val name = person.optString("name").takeIf { it.isNotBlank() } ?: return null
            return PersonMovies(id = id, name = name, movies = parseMovies(person, "movies"))
        }
        val related = Related(
            similar = parseMovies(relatedJson, "similar"),
            director = parsePerson("director"),
            actor = parsePerson("actor"),
        )

        return Context(lists = lists, collection = collection, universe = universe, related = related)
    }
}
