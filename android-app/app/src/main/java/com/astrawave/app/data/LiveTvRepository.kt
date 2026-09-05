package com.astrawave.app.data

import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream

/** Unified live-TV pipeline for AstraWave-authorized feeds and user-provided M3U/Xtream sources. */
data class LiveChannel(
    val id: String,
    val name: String,
    val normalizedName: String,
    val url: String,
    val group: String?,
    val logo: String?,
    val tvgId: String?,
    val source: String,
    val priority: Int,
)

data class LiveChannelGroup(
    val canonicalName: String,
    val displayName: String,
    val candidates: List<LiveChannel>,
    val currentProgram: XmlTvProgramme? = null,
    val nextProgram: XmlTvProgramme? = null,
) {
    val bestCandidate: LiveChannel? get() = candidates.minByOrNull { it.priority }
}

class LiveTvRepository {
    fun loadM3u(url: String, source: String = "M3U", priority: Int = 20): List<LiveChannel> {
        val text = SimpleHttp.getText(url)
        return M3uParser.parse(text).mapIndexed { index, channel ->
            LiveChannel(
                id = channel.tvgId?.takeIf { it.isNotBlank() } ?: "$source-$index",
                name = channel.name,
                normalizedName = normalizeChannelName(channel.name),
                url = channel.url,
                group = channel.group,
                logo = channel.logo,
                tvgId = channel.tvgId,
                source = source,
                priority = priority,
            )
        }
    }

    fun loadXtream(server: String, username: String, password: String): List<LiveChannel> =
        loadM3u(XtreamEndpoints.liveM3u(server, username, password), "Xtream", 15)

    fun loadXmlTv(url: String): List<XmlTvProgramme> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 30_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "AstraWave/0.3 EPG")
        connection.inputStream.use { raw ->
            val input = if (url.endsWith(".gz", ignoreCase = true)) GZIPInputStream(raw) else raw
            input.use { return XmlTvParser.parse(it) }
        }
    }

    fun merge(channelLists: List<List<LiveChannel>>, programmes: List<XmlTvProgramme> = emptyList()): List<LiveChannelGroup> {
        val byTvg = programmes.groupBy { it.channelId }
        val nowMs = System.currentTimeMillis()
        return channelLists.flatten()
            .filter { it.url.isNotBlank() && it.normalizedName.isNotBlank() }
            .distinctBy { "${it.source}:${it.normalizedName}:${it.url}" }
            .groupBy(::channelIdentityKey)
            .map { (identityKey, candidates) ->
                val ordered = candidates.sortedWith(compareBy<LiveChannel> { it.priority }.thenBy { it.source })
                val first = ordered.first()
                val schedule = ordered.asSequence()
                    .mapNotNull { it.tvgId?.takeIf(String::isNotBlank) }
                    .flatMap { byTvg[it].orEmpty().asSequence() }
                    .distinctBy { "${it.channelId}:${it.start}:${it.stop}:${it.title}" }
                    .sortedBy { parseXmlTvEpochMs(it.start) ?: Long.MAX_VALUE }
                    .toList()
                val current = schedule.firstOrNull { programme ->
                    val start = parseXmlTvEpochMs(programme.start) ?: return@firstOrNull false
                    val stop = parseXmlTvEpochMs(programme.stop) ?: return@firstOrNull false
                    nowMs in start until stop
                }
                val next = schedule.firstOrNull { programme ->
                    val start = parseXmlTvEpochMs(programme.start) ?: return@firstOrNull false
                    start > nowMs && programme != current
                }
                LiveChannelGroup(
                    canonicalName = identityKey,
                    displayName = preferredDisplayName(ordered),
                    candidates = ordered,
                    currentProgram = current,
                    nextProgram = next,
                )
            }
            .sortedWith(compareBy<LiveChannelGroup> { it.candidates.firstOrNull()?.group ?: "ZZZ" }.thenBy { it.displayName })
    }

    fun healthyCandidates(group: LiveChannelGroup): List<LiveChannel> = group.candidates.filter { candidate ->
        runCatching { StreamHealthChecker.check(candidate.url).reachable }.getOrDefault(false)
    }

    /** Returns only a health-verified candidate. A dead/unverified fallback is never called playable. */
    fun choosePlayable(group: LiveChannelGroup): LiveChannel? = healthyCandidates(group).firstOrNull()

    companion object {
        private val xmlTvFormats = listOf(
            "yyyyMMddHHmmss Z",
            "yyyyMMddHHmm Z",
            "yyyyMMddHHmmss",
            "yyyyMMddHHmm",
        )

        fun parseXmlTvEpochMs(raw: String): Long? {
            val value = raw.trim()
            if (value.isBlank()) return null
            xmlTvFormats.forEach { pattern ->
                runCatching {
                    val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                    val date: Date = parser.parse(value) ?: return@runCatching null
                    return date.time
                }
            }
            return null
        }

        /**
         * Channel names are the cross-provider identity because public playlists frequently use
         * incompatible tvg-id schemes for the same station. Keeping tvg-id on each candidate still
         * lets the merged group consume guide data from every known identifier.
         */
        fun channelIdentityKey(channel: LiveChannel): String =
            channel.normalizedName
                .takeIf { it.isNotBlank() }
                ?.let { "name:$it" }
                ?: channel.tvgId
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?.lowercase(Locale.US)
                    ?.let { "tvg:$it" }
                ?: "url:${channel.url}"

        fun normalizeChannelName(raw: String): String = raw.lowercase(Locale.US)
            .replace("+", " plus ")
            .replace("&", " and ")
            .replace(Regex("\\b(uhd|4k|fhd|hd|sd|hevc|h265|h264|60fps|50fps)\\b"), " ")
            .replace(Regex("\\b(us|usa|east|west|central|backup|alt|alternative|feed)\\b"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\([^)]*(?:hd|sd|uhd|4k|feed|backup)[^)]*\\)"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        private fun preferredDisplayName(channels: List<LiveChannel>): String =
            channels
                .sortedWith(compareBy<LiveChannel> { it.priority }.thenBy { it.name.length })
                .firstOrNull()
                ?.name
                ?: channels.first().name
    }
}
