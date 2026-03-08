package dev.standapp.engine.control

import dev.standapp.engine.entity.CommitInfo

object CommitFormatter {

    fun format(commits: List<CommitInfo>): String =
        commits.joinToString("\n") { commit ->
            "ID: ${commit.id}\nAuthor: ${commit.authorName} <${commit.authorEmail}>\nDate: ${commit.date}\nMessage: ${commit.message}\n---"
        }
}
