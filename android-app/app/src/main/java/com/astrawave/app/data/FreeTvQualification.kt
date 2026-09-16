package com.astrawave.app.data

data class FreeTvProbe(
    val streamStartedWithinTenSeconds: Boolean,
    val hasNowNextGuide: Boolean,
    val exposedAsPlayable: Boolean = true,
)

data class FreeTvQualificationResult(
    val qualified: Boolean,
    val playbackRate: Double,
    val guideRate: Double,
    val sampleCount: Int,
)

object FreeTvQualification {
    const val minimumSampleSize = 20
    const val minimumPlaybackRate = 0.90
    const val minimumGuideRate = 0.80

    fun evaluate(runs: List<List<FreeTvProbe>>): FreeTvQualificationResult {
        val samples = runs.flatten()
        val playbackRate = samples.count { it.streamStartedWithinTenSeconds }.toDouble() / samples.size.coerceAtLeast(1)
        val guideRate = samples.count { it.hasNowNextGuide }.toDouble() / samples.size.coerceAtLeast(1)
        val everyRunReliable = runs.size >= 3 && runs.all { run ->
            run.size >= minimumSampleSize &&
                run.count { it.streamStartedWithinTenSeconds }.toDouble() / run.size >= minimumPlaybackRate
        }
        val noDeadPlayableEntries = samples.none { it.exposedAsPlayable && !it.streamStartedWithinTenSeconds }
        return FreeTvQualificationResult(
            qualified = everyRunReliable && guideRate >= minimumGuideRate && noDeadPlayableEntries,
            playbackRate = playbackRate,
            guideRate = guideRate,
            sampleCount = samples.size,
        )
    }
}
