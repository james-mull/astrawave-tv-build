package com.astrawave.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudStreamPolicyTest {
    @Test
    fun enabledCustomRepositoryIsNotExecutionEligible() {
        val repo = CloudStreamRepositoryPreference(
            id = "custom",
            name = "Custom Repo",
            url = "https://example.com/repo.json",
            enabled = true,
            custom = true,
            reviewed = true,
            allowPluginExecution = true,
        )

        assertFalse(repo.executionEligible)
    }

    @Test
    fun enabledButUnreviewedRepositoryIsNotExecutionEligible() {
        val repo = CloudStreamRepositoryPreference(
            id = "community",
            name = "Community Repo",
            url = "https://example.com/repo.json",
            enabled = true,
            reviewed = false,
            allowPluginExecution = true,
        )

        assertFalse(repo.executionEligible)
    }

    @Test
    fun reviewedRepositoryStillRequiresExplicitExecutionPermission() {
        val repo = CloudStreamRepositoryPreference(
            id = "reviewed",
            name = "Reviewed Repo",
            url = "https://example.com/repo.json",
            enabled = true,
            reviewed = true,
            allowPluginExecution = false,
        )

        assertFalse(repo.executionEligible)
        assertTrue(repo.copy(allowPluginExecution = true).executionEligible)
    }
}
