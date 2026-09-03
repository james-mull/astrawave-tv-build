package com.astrawave.app.core

/** Inputs and outputs for AstraWave personalization/recommendation ranking. */
data class RecommendationProfile(
    val profileId: String,
    val preferredGenres: Set<String> = emptySet(),
    val dislikedGenres: Set<String> = emptySet(),
    val followedTeamIds: Set<String> = emptySet(),
    val favoriteItemIds: Set<String> = emptySet(),
    val watchlistItemIds: Set<String> = emptySet(),
    val completedItemIds: Set<String> = emptySet(),
    val recentItemIds: List<String> = emptyList(),
)

data class RecommendationCandidate(
    val id: String,
    val title: String,
    val mediaType: LibraryMediaType,
    val genres: Set<String> = emptySet(),
    val popularityScore: Double = 0.0,
    val freshnessScore: Double = 0.0,
    val availableSourceCount: Int = 0,
    val posterUrl: String? = null,
    val metadataSource: String? = null,
)

data class RecommendationReason(
    val code: String,
    val label: String,
    val weight: Double,
)

data class RankedRecommendation(
    val candidate: RecommendationCandidate,
    val score: Double,
    val reasons: List<RecommendationReason>,
)

/**
 * Deterministic local ranking used before any optional AI explanation/generation layer.
 * This keeps recommendations explainable and avoids fake model-derived confidence values.
 */
class RecommendationEngine {
    fun rank(
        profile: RecommendationProfile,
        candidates: List<RecommendationCandidate>,
        limit: Int = 50,
    ): List<RankedRecommendation> = candidates
        .asSequence()
        .filterNot { it.id in profile.completedItemIds }
        .map { candidate -> rankOne(profile, candidate) }
        .sortedWith(compareByDescending<RankedRecommendation> { it.score }.thenBy { it.candidate.title })
        .take(limit)
        .toList()

    private fun rankOne(profile: RecommendationProfile, candidate: RecommendationCandidate): RankedRecommendation {
        val reasons = buildList {
            val preferredMatches = candidate.genres.intersect(profile.preferredGenres).size
            if (preferredMatches > 0) add(RecommendationReason("genre_match", "Matches genres you like", preferredMatches * 18.0))

            val dislikedMatches = candidate.genres.intersect(profile.dislikedGenres).size
            if (dislikedMatches > 0) add(RecommendationReason("genre_dislike", "Contains genres you hide", dislikedMatches * -30.0))

            if (candidate.id in profile.favoriteItemIds) add(RecommendationReason("favorite", "Already a favorite", 10.0))
            if (candidate.id in profile.watchlistItemIds) add(RecommendationReason("watchlist", "On your watchlist", 8.0))
            if (candidate.availableSourceCount > 0) add(RecommendationReason("available", "Available from your connected sources", minOf(candidate.availableSourceCount, 4) * 4.0))
            if (candidate.freshnessScore > 0) add(RecommendationReason("fresh", "Recently released or updated", candidate.freshnessScore.coerceIn(0.0, 10.0)))
            if (candidate.popularityScore > 0) add(RecommendationReason("popular", "Popular with viewers", (candidate.popularityScore / 10.0).coerceIn(0.0, 8.0)))
        }
        return RankedRecommendation(candidate, reasons.sumOf { it.weight }, reasons)
    }
}
