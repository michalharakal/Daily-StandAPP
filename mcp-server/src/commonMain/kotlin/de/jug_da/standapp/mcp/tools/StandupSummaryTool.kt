package de.jug_da.standapp.mcp.tools

import de.jug_da.data.git.GitInfo
import de.jug_da.data.git.commitsByAuthorAndPeriod
import de.jug_da.data.git.getAllCommitsInPeriod
import de.jug_da.standapp.llm.getLLMSummarizer
import de.jug_da.standapp.mcp.methods.Tool
import de.jug_da.standapp.mcp.methods.ToolCallContent
import de.jug_da.standapp.mcp.methods.ToolCallResult
import de.jug_da.standapp.mcp.methods.ToolInputSchema
import de.jug_da.standapp.mcp.methods.ToolParameter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.days

/**
 * Standup Summary Tool for generating AI-powered standup summaries from Git commits.
 * 
 * This tool analyzes Git repository commits and uses LLM to create human-readable
 * summaries suitable for daily standup meetings. It integrates with the existing
 * JLama service from the LLM module and provides various customization options
 * for summary style and focus.
 */
class StandupSummaryTool : MCPTool {
    
    override val name: String = "standup_summary"
    
    override val description: String = 
        "Generate an AI-powered standup summary from Git commits. Uses LLM to create " +
        "human-readable summaries of development work for daily standups."
    
    override val category: String = "ai"
    
    override val requiresPermissions: List<String> = listOf("filesystem.read", "llm.inference")
    
    override fun getToolDefinition(): Tool {
        return Tool(
            name = name,
            description = description,
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "repository_path" to ToolParameter(
                        type = "string",
                        description = "Path to the Git repository to analyze",
                        required = true
                    ),
                    "days" to ToolParameter(
                        type = "integer",
                        description = "Number of days to look back for commits (default: 1, max: 7)",
                        required = false
                    ),
                    "author" to ToolParameter(
                        type = "string",
                        description = "Filter commits by author name or email (optional)",
                        required = false
                    ),
                    "style" to ToolParameter(
                        type = "string",
                        description = "Summary style preference",
                        required = false,
                        enum = listOf("concise", "detailed", "bullet_points", "narrative")
                    ),
                    "focus" to ToolParameter(
                        type = "string",
                        description = "Focus area for the summary",
                        required = false,
                        enum = listOf("features", "fixes", "refactoring", "all")
                    ),
                    "team_focused" to ToolParameter(
                        type = "boolean",
                        description = "Generate team-focused summary instead of individual-focused",
                        required = false
                    ),
                    "include_technical_details" to ToolParameter(
                        type = "boolean",
                        description = "Include technical implementation details in the summary",
                        required = false
                    )
                ),
                required = listOf("repository_path")
            )
        )
    }
    
    override fun validateArguments(arguments: JsonObject?): ValidationResult {
        if (arguments == null) {
            return ValidationResult.failure("Missing arguments")
        }
        
        val errors = mutableListOf<String>()
        
        // Validate repository_path
        val repositoryPath = arguments["repository_path"]?.jsonPrimitive?.content
        if (repositoryPath.isNullOrBlank()) {
            errors.add("repository_path is required and cannot be empty")
        }
        
        // Validate days parameter
        val days = arguments["days"]?.jsonPrimitive?.content?.toIntOrNull()
        if (days != null) {
            if (days < 1) {
                errors.add("days must be at least 1")
            }
            if (days > 7) {
                errors.add("days cannot exceed 7 for LLM processing performance")
            }
        }
        
        // Validate style parameter
        val style = arguments["style"]?.jsonPrimitive?.content
        if (style != null && style !in listOf("concise", "detailed", "bullet_points", "narrative")) {
            errors.add("style must be one of: concise, detailed, bullet_points, narrative")
        }
        
        // Validate focus parameter
        val focus = arguments["focus"]?.jsonPrimitive?.content
        if (focus != null && focus !in listOf("features", "fixes", "refactoring", "all")) {
            errors.add("focus must be one of: features, fixes, refactoring, all")
        }
        
        return if (errors.isEmpty()) {
            ValidationResult.success()
        } else {
            ValidationResult.failure(errors)
        }
    }
    
    override suspend fun execute(arguments: JsonObject?): ToolCallResult {
        return try {
            val repositoryPath = arguments?.get("repository_path")?.jsonPrimitive?.content
                ?: return createErrorResult("Missing repository_path")
            
            val days = arguments["days"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            val author = arguments["author"]?.jsonPrimitive?.content
            val style = arguments["style"]?.jsonPrimitive?.content ?: "concise"
            val focus = arguments["focus"]?.jsonPrimitive?.content ?: "all"
            val teamFocused = arguments["team_focused"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val includeTechnicalDetails = arguments["include_technical_details"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            
            // Get commits from Git repository
            val now = Clock.System.now()
            val startTime = now.minus(days.days)
            
            val commits = if (author != null) {
                commitsByAuthorAndPeriod(repositoryPath, author, startTime, now)
            } else {
                getAllCommitsInPeriod(repositoryPath, startTime, now)
            }
            
            if (commits.isEmpty()) {
                return ToolCallResult(
                    content = listOf(
                        ToolCallContent(
                            type = "text",
                            text = "No commits found for the specified criteria. No standup summary to generate."
                        )
                    )
                )
            }
            
            // Format commits for LLM processing
            val commitsText = formatCommitsForLLM(commits, focus, includeTechnicalDetails)
            
            // Create prompt for LLM
            val prompt = createStandupPrompt(commitsText, style, teamFocused, days, author)
            
            // Generate summary using LLM
            val llmSummarizer = getLLMSummarizer()
            val summary = llmSummarizer.summarize(prompt)
            
            ToolCallResult(
                content = listOf(
                    ToolCallContent(
                        type = "text",
                        text = summary
                    )
                )
            )
            
        } catch (e: Exception) {
            createErrorResult("Standup summary generation failed: ${e.message}")
        }
    }
    
    override fun isAvailable(): Boolean {
        return try {
            // Check if LLM service is available
            val llmSummarizer = getLLMSummarizer()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getMetadata(): Map<String, Any> {
        return mapOf(
            "version" to "1.0.0",
            "max_days" to 7,
            "supported_styles" to listOf("concise", "detailed", "bullet_points", "narrative"),
            "supported_focus" to listOf("features", "fixes", "refactoring", "all"),
            "supports_team_summaries" to true,
            "llm_backend" to "JLama"
        )
    }
    
    /**
     * Format commits for LLM processing based on focus and technical detail preferences.
     */
    private fun formatCommitsForLLM(
        commits: List<GitInfo>,
        focus: String,
        includeTechnicalDetails: Boolean
    ): String {
        val filteredCommits = when (focus) {
            "features" -> commits.filter { 
                it.message.lowercase().contains(Regex("feat|feature|add|implement|new"))
            }
            "fixes" -> commits.filter { 
                it.message.lowercase().contains(Regex("fix|bug|issue|resolve|patch"))
            }
            "refactoring" -> commits.filter { 
                it.message.lowercase().contains(Regex("refactor|cleanup|improve|optimize|restructure"))
            }
            else -> commits
        }
        
        return buildString {
            appendLine("Git commits from the repository:")
            appendLine()
            
            filteredCommits.sortedByDescending { it.whenDate }.forEach { commit ->
                appendLine("Commit: ${commit.message.trim()}")
                if (includeTechnicalDetails) {
                    appendLine("Author: ${commit.authorName}")
                    appendLine("Date: ${formatInstant(commit.whenDate)}")
                    appendLine("ID: ${commit.id.take(8)}")
                }
                appendLine()
            }
        }
    }
    
    /**
     * Create a structured prompt for the LLM to generate standup summaries.
     */
    private fun createStandupPrompt(
        commitsText: String,
        style: String,
        teamFocused: Boolean,
        days: Int,
        author: String?
    ): String {
        val period = if (days == 1) "yesterday" else "the last $days days"
        val perspective = if (teamFocused) "team" else "individual"
        val authorInfo = author?.let { " for $it" } ?: ""
        
        return buildString {
            appendLine("You are helping to generate a daily standup summary based on Git commits.")
            appendLine("Please create a $style $perspective standup summary from the following commits$authorInfo covering $period.")
            appendLine()
            
            when (style) {
                "concise" -> {
                    appendLine("Format the summary as a brief, focused update suitable for a quick standup meeting.")
                    appendLine("Keep it under 100 words and focus on the most important accomplishments.")
                }
                "detailed" -> {
                    appendLine("Format the summary as a comprehensive update with context and details.")
                    appendLine("Include background information and explain the significance of the work.")
                }
                "bullet_points" -> {
                    appendLine("Format the summary as clear bullet points organized by category:")
                    appendLine("• What was accomplished")
                    appendLine("• What is in progress")
                    appendLine("• What's coming next")
                }
                "narrative" -> {
                    appendLine("Format the summary as a flowing narrative that tells the story of the development work.")
                    appendLine("Connect the commits into a coherent progress story.")
                }
            }
            
            if (teamFocused) {
                appendLine("Focus on team progress, collaborative work, and overall project advancement.")
            } else {
                appendLine("Focus on individual contributions and personal development progress.")
            }
            
            appendLine()
            appendLine("Commits to summarize:")
            appendLine(commitsText)
            
            appendLine()
            appendLine("Please generate the standup summary now:")
        }
    }
    
    /**
     * Format instant for human readable display.
     */
    private fun formatInstant(instant: Instant): String {
        return instant.toString().substring(0, 19).replace("T", " ")
    }
    
    /**
     * Create an error result for tool execution failures.
     */
    private fun createErrorResult(message: String): ToolCallResult {
        return ToolCallResult(
            content = listOf(
                ToolCallContent(
                    type = "text",
                    text = "Error: $message"
                )
            ),
            isError = true
        )
    }
}