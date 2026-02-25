package de.jug_da.standapp.benchmark

/**
 * Formats [CommitEntry] list into the production prompt format
 * used by the MCP server (standapp-server.kt lines 59-61).
 */
object CommitFormatter {

    fun format(commits: List<CommitEntry>): String =
        commits.joinToString("\n") { commit ->
            "ID: ${commit.id}\nAuthor: ${commit.authorName} <${commit.authorEmail}>\nDate: ${commit.whenDate}\nMessage: ${commit.message}\n---"
        }
}
