package de.jug_da.data.git.service

import kotlinx.datetime.Instant

data class GitInfo(val commitText: String)

interface GitService {

    suspend fun commitsByAuthorAndPeriod(repoDir: String, author: String, start: Instant, end: Instant): List<GitInfo>

    fun commitsByAuthorAndPeriod(
        repoDir: String,
        author: String,
        start: Instant,
        end: Instant,
        callback: (List<GitInfo>) -> Unit
    )
}

expect fun getGitService(): GitService
