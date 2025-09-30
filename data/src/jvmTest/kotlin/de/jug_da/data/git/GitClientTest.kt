package de.jug_da.data.git

import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import java.io.File
import java.util.TimeZone
import java.util.Date

class GitClientTest {

    @Test
    fun `commits are filtered by author and period`() {
        val dir = createTempDirectory("repo").toFile()
        val git = Git.init().setDirectory(dir).call()

        val t1 = Instant.fromEpochMilliseconds(1_000_000)
        val t2 = Instant.fromEpochMilliseconds(2_000_000)

        commit(git, dir, "file1.txt", "Message1", "Alice", "alice@example.com", t1)
        commit(git, dir, "file2.txt", "Message2", "Bob", "bob@example.com", t2)

        val result = commitsByAuthorAndPeriod(
            dir.path,
            "Alice",
            Instant.fromEpochMilliseconds(500_000),
            Instant.fromEpochMilliseconds(1_500_000)
        )

        assertEquals(1, result.size)
        assertEquals("Message1", result.first().message)
    }

    private fun commit(git: Git, dir: File, fileName: String, msg: String, name: String, email: String, time: Instant) {
        val file = File(dir, fileName)
        file.writeText(msg)
        git.add().addFilepattern(fileName).call()
        val ident = PersonIdent(name, email, Date.from(time.toJavaInstant()), TimeZone.getTimeZone("UTC"))
        git.commit().setAuthor(ident).setCommitter(ident).setMessage(msg).call()
    }
}
