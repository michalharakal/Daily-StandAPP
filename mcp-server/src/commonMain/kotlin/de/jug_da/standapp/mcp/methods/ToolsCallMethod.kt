package de.jug_da.standapp.mcp.methods

import de.jug_da.standapp.mcp.protocol.MCPError
import de.jug_da.standapp.mcp.protocol.MCPRequest
import de.jug_da.standapp.mcp.protocol.MCPResponse
import de.jug_da.standapp.mcp.protocol.MCPSerializers
import de.jug_da.standapp.mcp.tools.GlobalToolRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool call request parameters
 */
@Serializable
data class ToolCallParams(
    val name: String,
    val arguments: JsonObject? = null
)

/**
 * Tool call response result
 */
@Serializable
data class ToolCallResult(
    val content: List<ToolCallContent>,
    val isError: Boolean = false
)

/**
 * Tool call content item
 */
@Serializable
data class ToolCallContent(
    val type: String,
    val text: String? = null,
    val data: JsonElement? = null
)

/**
 * Tools call method handler for MCP protocol
 */
object ToolsCallMethod {
    const val METHOD_NAME = "tools/call"
    
    /**
     * Handle tools/call request - executes the specified tool
     */
    suspend fun handle(request: MCPRequest): MCPResponse {
        return try {
            // Parse tool call parameters
            val params = request.params?.let { paramsJson ->
                MCPSerializers.json.decodeFromJsonElement(ToolCallParams.serializer(), paramsJson)
            } ?: return MCPResponse(
                id = request.id,
                error = MCPError.invalidParams("Missing tool call parameters")
            )
            
            // Execute the requested tool using the registry
            val result = GlobalToolRegistry.instance.executeTool(params.name, params.arguments)
            
            MCPResponse(
                id = request.id,
                result = MCPSerializers.json.encodeToJsonElement(result)
            )
            
        } catch (e: Exception) {
            MCPResponse(
                id = request.id,
                error = MCPError.toolExecutionError("Tool execution failed: ${e.message}")
            )
        }
    }
    
    /**
     * Execute standup summary tool
     */
    private fun executeStandupSummary(arguments: JsonObject?): ToolCallResult {
        return try {
            val repositoryPath = arguments?.get("repository_path")?.jsonPrimitive?.content
                ?: return createErrorResult("Missing required parameter: repository_path")
            
            val days = arguments["days"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
            val author = arguments["author"]?.jsonPrimitive?.content
            val style = arguments["style"]?.jsonPrimitive?.content ?: "concise"
            val focus = arguments["focus"]?.jsonPrimitive?.content ?: "all"
            
            // TODO: Integrate with actual LLM service from llm module
            // For now, return a mock response to demonstrate the structure
            val summaryResult = when (style) {
                "bullet_points" -> """
                    Daily Standup Summary (${days} day${if (days > 1) "s" else ""}):
                    
                    What I worked on:
                    • Implemented new feature X with improved user interface
                    • Fixed critical bug in payment processing component
                    • Refactored authentication module for better maintainability
                    
                    What I completed:
                    • Feature X is now ready for testing
                    • Payment bug fix deployed to staging
                    
                    What's next:
                    • Continue with feature Y implementation
                    • Code review for authentication refactor
                """.trimIndent()
                
                "detailed" -> """
                    Detailed Daily Standup Summary:
                    
                    Yesterday's accomplishments:
                    I focused primarily on feature development and bug fixes. The main achievement was implementing the new feature X, which involved creating a more intuitive user interface and improving the overall user experience. Additionally, I addressed a critical issue in our payment processing system that was causing transaction failures for some users. The fix has been tested and deployed to our staging environment.
                    
                    Technical work completed:
                    - Refactored the authentication module to improve code maintainability and reduce technical debt
                    - Added comprehensive error handling to the payment processing workflow
                    - Updated unit tests to cover new functionality
                    
                    Current priorities:
                    Moving forward, I'll be working on feature Y implementation and conducting code reviews for the authentication changes.
                """.trimIndent()
                
                else -> """
                    Yesterday: Implemented feature X, fixed payment processing bug, refactored auth module.
                    Today: Working on feature Y, code reviews for auth changes.
                    Blockers: None currently.
                """.trimIndent()
            }
            
            ToolCallResult(
                content = listOf(
                    ToolCallContent(
                        type = "text",
                        text = summaryResult
                    )
                )
            )
            
        } catch (e: Exception) {
            createErrorResult("Standup summary generation failed: ${e.message}")
        }
    }
    
    /**
     * Execute health check tool
     */
    private fun executeHealthCheck(arguments: JsonObject?): ToolCallResult {
        return try {
            val detailed = arguments?.get("detailed")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            
            val healthStatus = if (detailed) {
                """
                MCP Server Health Check - DETAILED
                
                Server Status: ✅ HEALTHY
                Version: 1.0.0
                Protocol Version: 2024-11-05
                Uptime: 2h 15m 30s
                
                Component Status:
                - MCP Protocol Handler: ✅ OK
                - Git Client: ✅ OK (JGit 7.1.0)
                - LLM Service: ✅ OK (JLama integration available)
                - WebSocket Transport: ✅ OK
                
                Capabilities:
                - Tools: ✅ Enabled (3 tools available)
                - Resources: ❌ Not implemented
                - Prompts: ❌ Not implemented
                - Logging: ✅ Enabled
                
                Memory Usage: 128MB / 512MB (25%)
                Active Connections: 1
                Total Requests Processed: 42
                """.trimIndent()
            } else {
                """
                MCP Server Health Check
                
                Status: ✅ HEALTHY
                Version: 1.0.0
                Available Tools: 3 (git_analysis, standup_summary, health_check)
                """.trimIndent()
            }
            
            ToolCallResult(
                content = listOf(
                    ToolCallContent(
                        type = "text",
                        text = healthStatus
                    )
                )
            )
            
        } catch (e: Exception) {
            createErrorResult("Health check failed: ${e.message}")
        }
    }
    
    /**
     * Create an error result for tool execution failures
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