@file:OptIn(ExperimentalTime::class)

package de.jug_da.data.git

import kotlin.time.Instant
import org.eclipse.jgit.api.Git
import java.io.File
import java.util.Date
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

@OptIn(ExperimentalTime::class)
actual fun commitsByAuthorAndPeriod(
    repoDir: String,
    author: String,
    start: Instant,
    end: Instant
): List<GitInfo> {
    return try {
        val git = Git.open(File(repoDir))
        git.use { gitRepo ->
            val startDate = Date.from(start.toJavaInstant())
            val endDate = Date.from(end.toJavaInstant())
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
                    whenDate = commit.authorIdent.whenAsInstant.toKotlinInstant(),
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
            val startDate = Date.from(start.toJavaInstant())
            val endDate = Date.from(end.toJavaInstant())
            gitRepo.log().call().filter { commit ->
                val whenDate = commit.authorIdent.`when`
                !whenDate.before(startDate) && !whenDate.after(endDate)
            }.map { commit ->
                GitInfo(
                    id = commit.id.name,
                    authorName = commit.authorIdent.name,
                    authorEmail = commit.authorIdent.emailAddress,
                    whenDate = commit.authorIdent.`when`.toInstant().toKotlinInstant(),
                    message = commit.fullMessage.trim()
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
