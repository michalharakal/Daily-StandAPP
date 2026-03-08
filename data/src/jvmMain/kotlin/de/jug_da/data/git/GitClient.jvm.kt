@file:Suppress("DEPRECATION")

package de.jug_da.data.git

import kotlinx.datetime.Instant
import org.eclipse.jgit.api.Git
import java.io.File
import java.util.Date

actual fun commitsByAuthorAndPeriod(
    repoDir: String,
    author: String,
    start: Instant,
    end: Instant
): List<GitInfo> {
    return try {
        val git = Git.open(File(repoDir))
        git.use { gitRepo ->
            val startDate = Date.from(java.time.Instant.ofEpochMilli(start.toEpochMilliseconds()))
            val endDate = Date.from(java.time.Instant.ofEpochMilli(end.toEpochMilliseconds()))
            gitRepo.log().call().filter { commit ->
                val whenDate = commit.authorIdent.`when`
                commit.authorIdent.name == author &&
                        !whenDate.before(startDate) &&
                        !whenDate.after(endDate)
            }.map { commit ->
                GitInfo(
                    id = commit.id.name,
                    authorName = commit.authorIdent.name,
                    authorEmail = commit.authorIdent.emailAddress,
                    whenDate = kotlinx.datetime.Instant.fromEpochMilliseconds(commit.authorIdent.whenAsInstant.toEpochMilli()),
                    message = commit.fullMessage.trim()
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}

actual fun getAllCommitsInPeriod(
    repoDir: String,
    start: Instant,
    end: Instant
): List<GitInfo> {
    return try {
        val git = Git.open(File(repoDir))
        git.use { gitRepo ->
            val startDate = Date.from(java.time.Instant.ofEpochMilli(start.toEpochMilliseconds()))
            val endDate = Date.from(java.time.Instant.ofEpochMilli(end.toEpochMilliseconds()))
            gitRepo.log().call().filter { commit ->
                val whenDate = commit.authorIdent.`when`
                !whenDate.before(startDate) && !whenDate.after(endDate)
            }.map { commit ->
                GitInfo(
                    id = commit.id.name,
                    authorName = commit.authorIdent.name,
                    authorEmail = commit.authorIdent.emailAddress,
                    whenDate = kotlinx.datetime.Instant.fromEpochMilliseconds(commit.authorIdent.`when`.toInstant().toEpochMilli()),
                    message = commit.fullMessage.trim()
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
