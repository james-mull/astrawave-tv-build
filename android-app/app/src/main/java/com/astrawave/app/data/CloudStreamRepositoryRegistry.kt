package com.astrawave.app.data

/**
 * Reviewed CloudStream repository definitions that AstraWave may expose through
 * the CloudStream bridge. Community repositories remain user opt-in.
 */
data class CloudStreamRepositoryDefinition(
    val id: String,
    val name: String,
    val repositoryUrl: String,
    val enabledByDefault: Boolean,
    val reviewed: Boolean,
    val note: String,
)

object CloudStreamRepositoryRegistry {
    val defaults: List<CloudStreamRepositoryDefinition> = listOf(
        CloudStreamRepositoryDefinition(
            id = "recloudstream-official",
            name = "CloudStream Providers Repository",
            repositoryUrl = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
            enabledByDefault = true,
            reviewed = true,
            note = "Built-in repository definition. Individual extensions still pass AstraWave eligibility and source-safety checks before they may contribute playback.",
        ),
    )
}
