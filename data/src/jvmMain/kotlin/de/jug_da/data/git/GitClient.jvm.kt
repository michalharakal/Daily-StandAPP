package de.jug_da.data.git

import org.eclipse.jgit.api.Git
import java.io.File
import kotlin.time.Instant

actual fun commitsByAuthorAndPeriod(
    repoDir: String,
    author: String,
    start: Instant,
    end: Instant
): List<GitInfo> {
    return try {
        val git = Git.open(File(repoDir))
        git.use { gitRepo ->
            val startInstant = java.time.Instant.ofEpochMilli(start.toEpochMilliseconds())
            val endInstant = java.time.Instant.ofEpochMilli(end.toEpochMilliseconds())
            gitRepo.log().call().filter { commit ->
                val whenInstant = commit.authorIdent.whenAsInstant
                commit.authorIdent.name == author &&
                        !whenInstant.isBefore(startInstant) &&
                        !whenInstant.isAfter(endInstant)
            }.map { commit ->
                GitInfo(
                    id = commit.id.name,
                    authorName = commit.authorIdent.name,
                    authorEmail = commit.authorIdent.emailAddress,
                    whenDate = Instant.fromEpochMilliseconds(commit.authorIdent.whenAsInstant.toEpochMilli()),
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
            val startInstant = java.time.Instant.ofEpochMilli(start.toEpochMilliseconds())
            val endInstant = java.time.Instant.ofEpochMilli(end.toEpochMilliseconds())
            gitRepo.log().call().filter { commit ->
                val whenInstant = commit.authorIdent.whenAsInstant
                !whenInstant.isBefore(startInstant) && !whenInstant.isAfter(endInstant)
            }.map { commit ->
                GitInfo(
                    id = commit.id.name,
                    authorName = commit.authorIdent.name,
                    authorEmail = commit.authorIdent.emailAddress,
                    whenDate = Instant.fromEpochMilliseconds(commit.authorIdent.whenAsInstant.toEpochMilli()),
                    message = commit.fullMessage.trim()
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
