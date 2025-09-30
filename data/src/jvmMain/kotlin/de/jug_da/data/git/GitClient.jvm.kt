package de.jug_da.data.git

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.datetime.toKotlinInstant
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
        git.use {
            val startDate = Date.from(start.toJavaInstant())
            val endDate = Date.from(end.toJavaInstant())
            it.log().call().filter { commit ->
                val whenDate = commit.authorIdent.when
                commit.authorIdent.name == author &&
                        !whenDate.before(startDate) &&
                        !whenDate.after(endDate)
            }.map { commit ->
                GitInfo(
                    id = commit.id.name,
                    authorName = commit.authorIdent.name,
                    authorEmail = commit.authorIdent.emailAddress,
                    whenDate = commit.authorIdent.when.toInstant().toKotlinInstant(),
                    message = commit.fullMessage.trim()
                )
            }
        }
    } catch (e: Exception) {
        emptyList()
    }
}
