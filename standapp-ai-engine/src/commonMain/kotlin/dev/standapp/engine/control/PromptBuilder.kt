package dev.standapp.engine.control

import dev.standapp.engine.entity.CommitInfo
import dev.standapp.engine.entity.PromptType

object DefaultPrompts {
    const val SYSTEM = "You are a developer assistant that creates concise standup summaries from Git commits."

    val SUMMARY_USER = """
        |You are a developer assistant that creates daily standup summaries from Git commits.
        |
        |Given the following Git commits, produce a concise standup report with exactly these three markdown headings:
        |
        |## Yesterday
        |(Summarise work completed based on the commits)
        |
        |## Today
        |(Infer planned work as a continuation, or state "Continue work on …")
        |
        |## Blockers
        |(List any obstacles mentioned in commit messages, or "None")
        |
        |Reference commit IDs where relevant. Be concise and actionable.
        |
        |Commits:
        |{{commits}}
    """.trimMargin()

    val JSON_USER = """
        |You are a developer assistant that creates structured standup data from Git commits.
        |
        |Given the following Git commits, produce a JSON object with this exact structure:
        |{
        |  "date": "<YYYY-MM-DD of the most recent commit>",
        |  "author": "<primary author name>",
        |  "categories": [
        |    {
        |      "name": "<category, e.g. Bug Fixes, Features, Refactoring, CI/Config, Documentation>",
        |      "commits": [
        |        {
        |          "id": "<commit hash from input>",
        |          "summary": "<one-line summary>",
        |          "status": "done | in-progress | unknown"
        |        }
        |      ]
        |    }
        |  ],
        |  "blockers": ["<any obstacles, or empty array>"]
        |}
        |
        |Rules:
        |- Output ONLY valid JSON, no markdown fences, no extra text.
        |- Every commit ID must come from the input — do not invent IDs.
        |- Group commits into logical categories.
        |- If there are multiple authors, use the most frequent as "author".
        |
        |Commits:
        |{{commits}}
    """.trimMargin()
}

class PromptBuilder(
    private val systemPrompt: String = DefaultPrompts.SYSTEM,
    private val summaryTemplate: String = DefaultPrompts.SUMMARY_USER,
    private val jsonTemplate: String = DefaultPrompts.JSON_USER,
) {
    fun buildUserPrompt(commits: List<CommitInfo>, promptType: PromptType): String {
        val formatted = CommitFormatter.format(commits)
        val template = when (promptType) {
            PromptType.SUMMARY -> summaryTemplate
            PromptType.JSON -> jsonTemplate
        }
        return template.replace("{{commits}}", formatted)
    }

    fun buildSystemPrompt(): String = systemPrompt
}
