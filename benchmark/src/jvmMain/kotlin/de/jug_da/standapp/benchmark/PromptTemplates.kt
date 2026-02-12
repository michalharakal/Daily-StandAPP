package de.jug_da.standapp.benchmark

/**
 * Prompt templates for the two benchmark output modes.
 */
object PromptTemplates {

    /**
     * Summary prompt — produces a free-text standup with Yesterday/Today/Blockers headings.
     */
    fun summaryPrompt(formattedCommits: String): String = """
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
        |$formattedCommits
    """.trimMargin()

    /**
     * JSON prompt — produces structured output conforming to the benchmark JSON schema.
     */
    fun jsonPrompt(formattedCommits: String): String = """
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
        |$formattedCommits
    """.trimMargin()
}
