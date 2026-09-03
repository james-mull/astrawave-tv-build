package com.astrawave.app.data

import com.astrawave.app.core.IptvSource
import com.astrawave.app.core.IptvSourceStatus
import com.astrawave.app.core.IptvSourceTestResult
import com.astrawave.app.core.IptvSourceType
import com.astrawave.app.core.IptvSourceValidation

/** Loads and validates user-managed IPTV sources before they enter Combined Live TV. */
class IptvSourceRepository(
    private val liveTvRepository: LiveTvRepository = LiveTvRepository(),
) {
    fun test(source: IptvSource): IptvSourceTestResult {
        val validationErrors = IptvSourceValidation.validate(source)
        if (validationErrors.isNotEmpty()) {
            return IptvSourceTestResult(
                sourceId = source.id,
                status = IptvSourceStatus.ERROR,
                channelCount = 0,
                guideProgramCount = 0,
                error = validationErrors.joinToString(" • "),
            )
        }

        return runCatching {
            val channels = loadChannels(source)
            val guide = loadGuide(source)
            IptvSourceTestResult(
                sourceId = source.id,
                status = if (channels.isEmpty()) IptvSourceStatus.DEGRADED else IptvSourceStatus.READY,
                channelCount = channels.size,
                guideProgramCount = guide.size,
                error = if (channels.isEmpty()) "Source connected but returned no live channels" else null,
            )
        }.getOrElse { error ->
            IptvSourceTestResult(
                sourceId = source.id,
                status = IptvSourceStatus.ERROR,
                channelCount = 0,
                guideProgramCount = 0,
                error = error.message ?: error::class.java.simpleName,
            )
        }
    }

    fun loadChannels(source: IptvSource): List<LiveChannel> {
        if (!source.enabled) return emptyList()
        return when (source.type) {
            IptvSourceType.M3U -> liveTvRepository.loadM3u(
                url = requireNotNull(source.m3uUrl),
                source = source.name,
                priority = source.priority,
            )
            IptvSourceType.XTREAM -> liveTvRepository.loadXtream(
                server = requireNotNull(source.xtreamServer),
                username = requireNotNull(source.xtreamUsername),
                password = requireNotNull(source.xtreamPassword),
            ).map { it.copy(source = source.name, priority = source.priority) }
        }
    }

    fun loadGuide(source: IptvSource): List<XmlTvProgramme> {
        if (!source.enabled) return emptyList()
        val xmlTv = source.xmlTvUrl?.takeIf { it.isNotBlank() }
            ?: if (source.type == IptvSourceType.XTREAM) {
                XtreamEndpoints.xmlTv(
                    requireNotNull(source.xtreamServer),
                    requireNotNull(source.xtreamUsername),
                    requireNotNull(source.xtreamPassword),
                )
            } else null
        return xmlTv?.let(liveTvRepository::loadXmlTv).orEmpty()
    }

    fun combined(sources: List<IptvSource>): List<LiveChannelGroup> {
        val enabled = sources.filter { it.enabled }
        val channels = enabled.map { loadChannels(it) }
        val guide = enabled.flatMap { loadGuide(it) }
        return liveTvRepository.merge(channels, guide)
    }
}
