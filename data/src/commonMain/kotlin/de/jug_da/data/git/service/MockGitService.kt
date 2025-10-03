@file:OptIn(ExperimentalTime::class)

package de.jug_da.data.git.service

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class MockGitService : GitService {
    override suspend fun commitsByAuthorAndPeriod(
        repoDir: String,
        author: String,
        start: Instant,
        end: Instant
    ): List<GitInfo> = mockData()


    override fun commitsByAuthorAndPeriod(
        repoDir: String,
        author: String,
        start: Instant,
        end: Instant,
        callback: (List<GitInfo>) -> Unit
    ) {
        callback(mockData())
    }

    private fun mockData(): List<GitInfo> = listOf(
        GitInfo("refactor: remove unnecessary function declaration"),
        GitInfo("fix typo in readme"),
        GitInfo("Avoid using nonstandard assembly syntax for NEON")
    )
}