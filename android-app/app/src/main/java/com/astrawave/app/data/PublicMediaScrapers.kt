package com.astrawave.app.data

import com.astrawave.app.core.AuthorizedScraper
import com.astrawave.app.core.ScrapeRequest
import com.astrawave.app.core.ScrapedLink
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Conservative Internet Archive provider. Search results are only promoted to playable
 * links when the item explicitly advertises a public-domain or Creative Commons license.
 * Hosting on archive.org alone is not treated as permission to redistribute/play.
 */
class InternetArchiveScraper : AuthorizedScraper {
    override val id: String = "internet_archive_open"
    override val displayName: String = "Internet Archive Open Media"
    override val allowedHosts: Set<String> = setOf("archive.org")

    override suspend fun search(request: ScrapeRequest): List<ScrapedLink> {
        if (request.season != null || request.episode != null) return emptyList()

        val title = escapeLucene(request.title)
        val yearClause = request.year?.let { " AND year:$it" } ?: ""
        val query = "title:(\"$title\") AND mediatype:(movies)$yearClause"
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
        val url = "https://archive.org/advancedsearch.php?q=$encoded&fl[]=identifier&rows=8&page=1&output=json"
        val docs = JSONObject(SimpleHttp.getText(url))
            .optJSONObject("response")
            ?.optJSONArray("docs") ?: return emptyList()

        val links = mutableListOf<ScrapedLink>()
        for (i in 0 until docs.length()) {
            val identifier = docs.optJSONObject(i)?.optString("identifier").orEmpty()
            if (identifier.isBlank()) continue
            links += resolveIdentifier(identifier)
        }
        return links.distinctBy { it.url }
    }

    private fun resolveIdentifier(identifier: String): List<ScrapedLink> {
        val metadata = JSONObject(SimpleHttp.getText("https://archive.org/metadata/${encodePath(identifier)}"))
        val meta = metadata.optJSONObject("metadata") ?: return emptyList()
        val license = listOf(
            meta.optString("licenseurl"),
            meta.optString("license"),
            meta.optString("rights")
        ).joinToString(" ").lowercase()

        if (!isExplicitlyReusable(license)) return emptyList()

        val title = meta.optString("title").ifBlank { identifier }
        val creator = when (val raw = meta.opt("creator")) {
            is String -> raw
            is JSONArray -> (0 until raw.length()).mapNotNull { raw.optString(it).takeIf(String::isNotBlank) }.joinToString(", ")
            else -> ""
        }
        val files = metadata.optJSONArray("files") ?: return emptyList()

        return buildList {
            for (i in 0 until files.length()) {
                val file = files.optJSONObject(i) ?: continue
                val name = file.optString("name")
                if (!isPlayableVideo(name, file.optString("format"))) continue
                val direct = "https://archive.org/download/${encodePath(identifier)}/${encodePathPreservingSlashes(name)}"
                add(
                    ScrapedLink(
                        url = direct,
                        sourceName = displayName,
                        quality = qualityLabel(file),
                        mimeType = mimeFor(name),
                        direct = true,
                        licenseLabel = licenseLabel(license),
                        attribution = buildString {
                            append(title)
                            if (creator.isNotBlank()) append(" — ").append(creator)
                        }
                    )
                )
            }
        }.sortedByDescending { qualityRank(it.quality) }.take(4)
    }

    private fun isExplicitlyReusable(text: String): Boolean =
        listOf(
            "publicdomain", "public domain", "creativecommons.org/licenses/",
            "cc0", "cc by", "cc-by", "creative commons"
        ).any(text::contains)

    private fun isPlayableVideo(name: String, format: String): Boolean {
        val lower = name.lowercase()
        val videoExt = lower.endsWith(".mp4") || lower.endsWith(".m4v") || lower.endsWith(".webm")
        if (!videoExt) return false
        val f = format.lowercase()
        return !f.contains("thumbnail") && !f.contains("metadata")
    }

    private fun qualityLabel(file: JSONObject): String? {
        val name = file.optString("name").lowercase()
        val title = file.optString("title").lowercase()
        val joined = "$name $title"
        return when {
            "2160" in joined || "4k" in joined -> "4K"
            "1080" in joined -> "1080p"
            "720" in joined -> "720p"
            "480" in joined -> "480p"
            else -> null
        }
    }

    private fun qualityRank(q: String?): Int = when (q) {
        "4K" -> 4
        "1080p" -> 3
        "720p" -> 2
        "480p" -> 1
        else -> 0
    }

    private fun licenseLabel(text: String): String = when {
        "publicdomain" in text || "public domain" in text -> "Public Domain"
        "cc0" in text -> "CC0"
        "creativecommons.org/licenses/by-sa" in text || "cc by-sa" in text -> "CC BY-SA"
        "creativecommons.org/licenses/by" in text || "cc by" in text -> "CC BY"
        else -> "Creative Commons"
    }

    private fun mimeFor(name: String): String? = when {
        name.endsWith(".mp4", true) || name.endsWith(".m4v", true) -> "video/mp4"
        name.endsWith(".webm", true) -> "video/webm"
        else -> null
    }

    private fun escapeLucene(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun encodePath(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
    private fun encodePathPreservingSlashes(value: String): String = value.split('/').joinToString("/") { encodePath(it) }
}

/**
 * AstraWave-controlled manifest provider for authorized/free VOD and live channels.
 * The service can publish a simple JSON array of streams without requiring an app release.
 */
class AstraWaveAuthorizedFeedScraper(
    private val manifestUrl: String,
    override val allowedHosts: Set<String>
) : AuthorizedScraper {
    override val id: String = "astrawave_authorized_feed"
    override val displayName: String = "AstraWave Free"

    override suspend fun search(request: ScrapeRequest): List<ScrapedLink> {
        val root = JSONObject(SimpleHttp.getText(manifestUrl))
        val items = root.optJSONArray("items") ?: return emptyList()
        val wanted = normalize(request.title)

        return buildList {
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                if (normalize(item.optString("title")) != wanted) continue
                if (request.year != null && item.optInt("year", request.year) != request.year) continue
                val streams = item.optJSONArray("streams") ?: continue
                for (j in 0 until streams.length()) {
                    val stream = streams.optJSONObject(j) ?: continue
                    val url = stream.optString("url")
                    if (url.isBlank()) continue
                    add(
                        ScrapedLink(
                            url = url,
                            sourceName = displayName,
                            quality = stream.optString("quality").takeIf(String::isNotBlank),
                            mimeType = stream.optString("mimeType").takeIf(String::isNotBlank),
                            language = stream.optString("language").takeIf(String::isNotBlank),
                            direct = stream.optBoolean("direct", true),
                            licenseLabel = item.optString("license").takeIf(String::isNotBlank),
                            attribution = item.optString("attribution").takeIf(String::isNotBlank)
                        )
                    )
                }
            }
        }
    }

    private fun normalize(value: String): String = value.lowercase().filter(Char::isLetterOrDigit)
}
