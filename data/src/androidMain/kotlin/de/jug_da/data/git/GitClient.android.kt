package de.jug_da.data.git

import kotlinx.datetime.Instant

actual fun commitsByAuthorAndPeriod(
    repoDir: String,
    author: String,
    start: Instant,
    end: Instant
): List<GitInfo> {
    TODO("Not yet implemented")
}