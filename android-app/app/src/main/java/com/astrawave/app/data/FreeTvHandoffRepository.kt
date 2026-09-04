package com.astrawave.app.data

import org.json.JSONObject

data class FreeTvHandoff(
    val id: String,
    val name: String,
    val group: String,
    val tvgId: String?,
    val actionUrl: String,
    val provider: String?,
)

/**
 * Loads reviewed official-provider handoffs from the AstraWave Free TV registry.
 * These are intentionally kept separate from direct media streams so provider
 * pages are never inserted into the M3U or misrepresented as Media3-playable URLs.
 */
class FreeTvHandoffRepository(
    private val registryUrl: String = DEFAULT_REGISTRY_URL,
) {
    fun load(): List<FreeTvHandoff> {
        val root = JSONObject(SimpleHttp.getText(registryUrl))
        val channels = root.optJSONArray("channels") ?: return emptyList()
        return buildList {
            for (index in 0 until channels.length()) {
                val item = channels.optJSONObject(index) ?: continue
                if (!item.optString("playbackMode").equals("external", ignoreCase = true)) continue
                if (item.optString("rightsStatus") !in ALLOWED_RIGHTS) continue
                val url = item.optString("streamUrl").takeIf { it.startsWith("https://") } ?: continue
                add(
                    FreeTvHandoff(
                        id = item.optString("id", "handoff-$index"),
                        name = item.optString("name", "Free TV"),
                        group = item.optString("group", "Premium Free"),
                        tvgId = item.optString("tvgId").takeIf(String::isNotBlank),
                        actionUrl = url,
                        provider = item.optString("provider").takeIf(String::isNotBlank),
                    ),
                )
            }
        }
    }

    companion object {
        const val DEFAULT_REGISTRY_URL =
            "https://raw.githubusercontent.com/james-mull/astrawave-tv-build/feature/nuvio-core-rebuild/astrawave-free-tv/sources.json"

        private val ALLOWED_RIGHTS = setOf(
            "official",
            "public-domain",
            "creative-commons",
            "authorized-redistribution",
        )
    }
}
