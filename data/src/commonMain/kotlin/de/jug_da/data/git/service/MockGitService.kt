package de.jug_da.data.git.service

import kotlinx.datetime.Instant

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

    private fun mockData(): List<GitInfo> = listOf()
}