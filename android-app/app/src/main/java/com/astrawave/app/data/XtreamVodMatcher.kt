package com.astrawave.app.data

/** Pure matching rules shared by Xtream VOD resolution and JVM regression tests. */
object XtreamVodMatcher {
    fun normalizeTitle(value: String): String = value.lowercase()
        .replace(Regex("\\b(4k|uhd|2160p|1080p|720p|fhd|hd)\\b"), " ")
        .replace(Regex("\\(?\\b(19|20)\\d{2}\\b\\)?"), " ")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun titleScore(expected: String, candidate: String): Int {
        val a = normalizeTitle(expected)
        val b = normalizeTitle(candidate)
        if (a.isBlank() || b.isBlank()) return 0
        return when {
            a == b -> 120
            a.startsWith(b) || b.startsWith(a) -> 90
            a.contains(b) || b.contains(a) -> 70
            else -> 0
        }
    }

    fun yearScore(expected: Int?, candidate: Int?): Int = when {
        expected == null || candidate == null -> 0
        expected == candidate -> 40
        kotlin.math.abs(expected - candidate) <= 1 -> 10
        else -> -1
    }

    fun episodeMatches(
        requestedSeason: Int,
        requestedEpisode: Int,
        candidateSeason: Int,
        candidateEpisode: Int,
    ): Boolean = candidateSeason == requestedSeason && candidateEpisode == requestedEpisode
}
