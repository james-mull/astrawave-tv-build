package com.astrawave.app.data

/**
 * CloudStream repository definitions exposed by AstraWave.
 *
 * Only the upstream reCloudStream repository is enabled automatically. Community
 * repositories remain opt-in and every extension still has to pass AstraWave's
 * health/source eligibility checks before it can contribute playback.
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
            note = "Upstream CloudStream extension repository. Extensions still pass AstraWave eligibility checks before contributing playback.",
        ),
        CloudStreamRepositoryDefinition(
            id = "cloudstream-community-aggregator",
            name = "CloudStream Community Aggregator",
            repositoryUrl = "https://raw.githubusercontent.com/crxnkzziszxmbi3-sys/cloudstream-custom-repo/main/repo.json",
            enabledByDefault = false,
            reviewed = false,
            note = "Community-maintained aggregator that tracks multiple CloudStream repositories. Opt-in only; individual plugins are not automatically trusted.",
        ),
        CloudStreamRepositoryDefinition(
            id = "hexated",
            name = "Hexated Extensions",
            repositoryUrl = "https://raw.githubusercontent.com/hexated/cloudstream-extensions-hexated/builds/repo.json",
            enabledByDefault = false,
            reviewed = false,
            note = "Community repository. Opt-in and health-checked before use.",
        ),
        CloudStreamRepositoryDefinition(
            id = "phisher98",
            name = "Phisher98 Extensions",
            repositoryUrl = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/builds/repo.json",
            enabledByDefault = false,
            reviewed = false,
            note = "Community repository. Opt-in and health-checked before use.",
        ),
        CloudStreamRepositoryDefinition(
            id = "cs-karma",
            name = "CS-Karma Extensions",
            repositoryUrl = "https://raw.githubusercontent.com/Kraptor123/Cs-Karma/builds/repo.json",
            enabledByDefault = false,
            reviewed = false,
            note = "Community repository. Opt-in and health-checked before use.",
        ),
        CloudStreamRepositoryDefinition(
            id = "adam-knight-mega",
            name = "Adam Knight Mega Repo",
            repositoryUrl = "https://raw.githubusercontent.com/admknight/CloudstreamExtensions/builds/repo.json",
            enabledByDefault = false,
            reviewed = false,
            note = "Large community repository. Opt-in only; plugins must be individually validated before playback.",
        ),
        CloudStreamRepositoryDefinition(
            id = "codegeasse",
            name = "Codegeasse CloudStream Repo",
            repositoryUrl = "https://raw.githubusercontent.com/codegeasse1/codegeasse-cloudstream-repos/builds/repo.json",
            enabledByDefault = false,
            reviewed = false,
            note = "Community repository. Opt-in and health-checked before use.",
        ),
    )
}
