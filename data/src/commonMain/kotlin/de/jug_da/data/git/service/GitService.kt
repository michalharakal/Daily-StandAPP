@file:OptIn(ExperimentalTime::class)

package de.jug_da.data.git.service

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class GitInfo(val commitText: String)

interface GitService {

    suspend fun commitsByAuthorAndPeriod(repoDir: String, author: String, start: Instant, end: Instant): List<GitInfo>

    @OptIn(ExperimentalTime::class)
    fun commitsByAuthorAndPeriod(
        repoDir: String,
        author: String,
        start: Instant,
        end: Instant,
        callback: (List<GitInfo>) -> Unit
    )
}

expect fun getGitService(): GitService
