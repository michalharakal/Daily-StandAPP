package de.jug_da.standapp.mcp.tools

import de.jug_da.data.git.GitInfo
import de.jug_da.data.git.commitsByAuthorAndPeriod
import de.jug_da.data.git.getAllCommitsInPeriod
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
 * Git Analysis Tool for analyzing repository commits and generating insights.
 * 
 * This tool provides comprehensive analysis of Git repository commits over
 * a specified time period, with filtering capabilities by author, branch,
 * and other criteria. It integrates with the existing GitClient from the
 * data module to provide commit information, statistics, and summaries.
 */
class GitAnalysisTool : MCPTool {
    
    override val name: String = "git_analysis"
    
    override val description: String = 
        "Analyze Git repository commits for a specified time period. Returns commit information, " +
        "statistics, and change summaries for daily standup preparation."
    
    override val category: String = "git"
    
    override val requiresPermissions: List<String> = listOf("filesystem.read")
    
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
                        description = "Number of days to look back for commits (default: 1, max: 30)",
                        required = false
                    ),
                    "author" to ToolParameter(
                        type = "string",
                        description = "Filter commits by author name or email (optional)",
                        required = false
                    ),
                    "branch" to ToolParameter(
                        type = "string",
                        description = "Git branch to analyze (default: current branch)",
                        required = false
                    ),
                    "include_stats" to ToolParameter(
                        type = "boolean",
                        description = "Include detailed statistics (commit count, files changed)",
                        required = false
                    ),
                    "format" to ToolParameter(
                        type = "string",
                        description = "Output format for the analysis results",
                        required = false,
                        enum = listOf("summary", "detailed", "json")
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
            if (days > 30) {
                errors.add("days cannot exceed 30 for performance reasons")
            }
        }
        
        // Validate format parameter
        val format = arguments["format"]?.jsonPrimitive?.content
        if (format != null && format !in listOf("summary", "detailed", "json")) {
            errors.add("format must be one of: summary, detailed, json")
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
            val includeStats = arguments["include_stats"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val format = arguments["format"]?.jsonPrimitive?.content ?: "summary"
            
            // Calculate time range
            val now = Clock.System.now()
            val startTime = now.minus(days.days)
            
            // Get commits based on author filter
            val commits = if (author != null) {
                commitsByAuthorAndPeriod(repositoryPath, author, startTime, now)
            } else {
                getAllCommitsInPeriod(repositoryPath, startTime, now)
            }
            
            // Generate analysis based on format
            val analysisResult = when (format) {
                "json" -> generateJsonAnalysis(commits, days, author, includeStats)
                "detailed" -> generateDetailedAnalysis(commits, days, author, includeStats)
                else -> generateSummaryAnalysis(commits, days, author, includeStats)
            }
            
            ToolCallResult(
                content = listOf(
                    ToolCallContent(
                        type = "text",
                        text = analysisResult
                    )
                )
            )
            
        } catch (e: Exception) {
            createErrorResult("Git analysis failed: ${e.message}")
        }
    }
    
    override fun isAvailable(): Boolean {
        // Check if Git functionality is available
        return try {
            // Simple availability check - in real implementation might check for Git binary
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override fun getMetadata(): Map<String, Any> {
        return mapOf(
            "version" to "1.0.0",
            "supports_branches" to false, // TODO: Implement branch filtering
            "max_days" to 30,
            "supported_formats" to listOf("summary", "detailed", "json")
        )
    }
    
    
    /**
     * Generate summary analysis format.
     */
    private fun generateSummaryAnalysis(
        commits: List<GitInfo>,
        days: Int,
        author: String?,
        includeStats: Boolean
    ): String {
        val period = if (days == 1) "yesterday" else "last $days days"
        val authorFilter = author?.let { " by $it" } ?: ""
        
        return buildString {
            appendLine("Git Analysis Summary ($period$authorFilter)")
            appendLine("=" .repeat(50))
            appendLine()
            
            if (commits.isEmpty()) {
                appendLine("No commits found for the specified criteria.")
                return@buildString
            }
            
            appendLine("📊 Commit Statistics:")
            appendLine("   Total commits: ${commits.size}")
            
            if (includeStats) {
                val authors = commits.map { it.authorName }.distinct()
                appendLine("   Authors involved: ${authors.size}")
                appendLine("   Authors: ${authors.joinToString(", ")}")
            }
            
            appendLine()
            appendLine("📝 Recent Commits:")
            
            commits.take(5).forEach { commit ->
                appendLine("   • ${commit.message.take(60)}${if (commit.message.length > 60) "..." else ""}")
                appendLine("     by ${commit.authorName} on ${formatInstant(commit.whenDate)}")
            }
            
            if (commits.size > 5) {
                appendLine("   ... and ${commits.size - 5} more commits")
            }
            
            appendLine()
            appendLine("🔗 Repository: $repositoryPath") // Remove actual path in real implementation for security
        }
    }
    
    /**
     * Generate detailed analysis format.
     */
    private fun generateDetailedAnalysis(
        commits: List<GitInfo>,
        days: Int,
        author: String?,
        includeStats: Boolean
    ): String {
        val period = if (days == 1) "yesterday" else "last $days days"
        val authorFilter = author?.let { " by $it" } ?: ""
        
        return buildString {
            appendLine("Detailed Git Analysis ($period$authorFilter)")
            appendLine("=" .repeat(60))
            appendLine()
            
            if (commits.isEmpty()) {
                appendLine("No commits found for the specified criteria.")
                appendLine()
                appendLine("Suggestions:")
                appendLine("• Check if the repository path is correct")
                appendLine("• Verify the author name/email if specified")
                appendLine("• Try increasing the number of days to look back")
                return@buildString
            }
            
            // Statistics section
            appendLine("📊 Detailed Statistics:")
            appendLine("   Total commits: ${commits.size}")
            
            val authors = commits.groupBy { it.authorName }
            appendLine("   Number of contributors: ${authors.size}")
            
            authors.forEach { (author, authorCommits) ->
                appendLine("   • $author: ${authorCommits.size} commits")
            }
            
            appendLine()
            appendLine("🕒 Commit Timeline:")
            
            commits.sortedByDescending { it.whenDate }.forEach { commit ->
                appendLine("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                appendLine("📝 ${commit.message}")
                appendLine("👤 Author: ${commit.authorName} <${commit.authorEmail}>")
                appendLine("📅 Date: ${formatInstant(commit.whenDate)}")
                appendLine("🔗 Commit: ${commit.id.take(8)}")
                appendLine()
            }
            
            appendLine("Analysis generated at ${formatInstant(Clock.System.now())}")
        }
    }
    
    /**
     * Generate JSON analysis format.
     */
    private fun generateJsonAnalysis(
        commits: List<GitInfo>,
        days: Int,
        author: String?,
        includeStats: Boolean
    ): String {
        // This would typically use kotlinx.serialization, but for simplicity
        // we'll construct JSON manually here
        return buildString {
            appendLine("{")
            appendLine("  \"analysis\": {")
            appendLine("    \"period_days\": $days,")
            appendLine("    \"author_filter\": ${author?.let { "\"$it\"" } ?: "null"},")
            appendLine("    \"total_commits\": ${commits.size},")
            appendLine("    \"generated_at\": \"${Clock.System.now()}\",")
            appendLine("    \"commits\": [")
            
            commits.forEachIndexed { index, commit ->
                appendLine("      {")
                appendLine("        \"id\": \"${commit.id}\",")
                appendLine("        \"message\": \"${commit.message.replace("\"", "\\\"")}\",")
                appendLine("        \"author_name\": \"${commit.authorName}\",")
                appendLine("        \"author_email\": \"${commit.authorEmail}\",")
                appendLine("        \"timestamp\": \"${commit.whenDate}\"")
                append("      }")
                if (index < commits.size - 1) appendLine(",")
                else appendLine()
            }
            
            appendLine("    ]")
            
            if (includeStats) {
                val authors = commits.groupBy { it.authorName }
                appendLine("    ,\"statistics\": {")
                appendLine("      \"authors\": {")
                authors.entries.forEachIndexed { index, (author, authorCommits) ->
                    append("        \"$author\": ${authorCommits.size}")
                    if (index < authors.size - 1) appendLine(",")
                    else appendLine()
                }
                appendLine("      }")
                appendLine("    }")
            }
            
            appendLine("  }")
            append("}")
        }
    }
    
    /**
     * Format instant for human readable display.
     */
    private fun formatInstant(instant: Instant): String {
        // Simple formatting - in real implementation would use proper date formatting
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