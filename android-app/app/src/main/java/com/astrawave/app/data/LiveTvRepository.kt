package com.astrawave.app.data

import java.io.ByteArrayInputStream
import java.util.Locale

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
        val xml = SimpleHttp.getText(url)
        return XmlTvParser.parse(ByteArrayInputStream(xml.toByteArray()))
    }

    fun merge(channelLists: List<List<LiveChannel>>, programmes: List<XmlTvProgramme> = emptyList()): List<LiveChannelGroup> {
        val byTvg = programmes.groupBy { it.channelId }
        return channelLists.flatten().groupBy { it.normalizedName }.map { (_, candidates) ->
            val ordered = candidates.sortedWith(compareBy<LiveChannel> { it.priority }.thenBy { it.source })
            val first = ordered.first()
            val guide = ordered.asSequence().mapNotNull { it.tvgId }.mapNotNull { byTvg[it]?.firstOrNull() }.firstOrNull()
            LiveChannelGroup(
                canonicalName = first.normalizedName,
                displayName = ordered.map { it.name }.maxByOrNull { it.length } ?: first.name,
                candidates = ordered,
                currentProgram = guide,
            )
        }.sortedWith(compareBy<LiveChannelGroup> { it.candidates.firstOrNull()?.group ?: "ZZZ" }.thenBy { it.displayName })
    }

    fun healthyCandidates(group: LiveChannelGroup): List<LiveChannel> = group.candidates.filter { candidate ->
        runCatching { StreamHealthChecker.check(candidate.url).reachable }.getOrDefault(false)
    }

    fun choosePlayable(group: LiveChannelGroup): LiveChannel? = healthyCandidates(group).firstOrNull() ?: group.bestCandidate

    companion object {
        fun normalizeChannelName(raw: String): String = raw.lowercase(Locale.US)
            .replace(Regex("\\b(uhd|4k|fhd|hd|sd|hevc|h265|h264|60fps|50fps)\\b"), " ")
            .replace(Regex("\\b(us|usa|east|west|central|backup|alt|alternative)\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")
    }
}
