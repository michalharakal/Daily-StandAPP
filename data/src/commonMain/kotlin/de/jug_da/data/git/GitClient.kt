@file:OptIn(ExperimentalTime::class)

package de.jug_da.data.git

import kotlin.time.ExperimentalTime
import kotlin.time.Instant

expect fun commitsByAuthorAndPeriod(repoDir: String, author: String, start: Instant, end: Instant): List<GitInfo>

expect fun getAllCommitsInPeriod(
    repoDir: String,
    start: Instant,
    end: Instant
): List<GitInfo>

