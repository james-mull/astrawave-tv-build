package com.astrawave.app.core

import java.net.URI

/**
 * Contract for online source discovery where AstraWave has permission to query
 * and play or present the returned media. This layer intentionally rejects
 * unknown/unapproved hosts.
 */
interface AuthorizedScraper {
    val id: String
    val displayName: String
    val allowedHosts: Set<String>

    suspend fun search(request: ScrapeRequest): List<ScrapedLink>

    fun isAllowed(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        return allowedHosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
    }
}

data class ScrapeRequest(
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val externalIds: Map<String, String> = emptyMap()
)

data class ScrapedLink(
    val url: String,
    val sourceName: String,
    val quality: String? = null,
    val mimeType: String? = null,
    val language: String? = null,
    val direct: Boolean = true,
    val licenseLabel: String? = null,
    val attribution: String? = null
)

class AuthorizedScraperRegistry(
    private val scrapers: MutableList<AuthorizedScraper> = mutableListOf()
) {
    fun register(scraper: AuthorizedScraper) {
        scrapers.removeAll { it.id == scraper.id }
        scrapers += scraper
    }

    suspend fun searchAll(request: ScrapeRequest): List<ScrapedLink> =
        scrapers.flatMap { scraper ->
            runCatching { scraper.search(request) }
                .getOrDefault(emptyList())
                .filter { scraper.isAllowed(it.url) }
        }
        .distinctBy { it.url }
}
