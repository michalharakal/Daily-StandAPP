package de.jug_da.standapp.mcp.methods

import de.jug_da.standapp.mcp.protocol.MCPError
import de.jug_da.standapp.mcp.protocol.MCPRequest
import de.jug_da.standapp.mcp.protocol.MCPResponse
import de.jug_da.standapp.mcp.protocol.MCPSerializers
import de.jug_da.standapp.mcp.tools.GlobalToolRegistry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Tool parameter schema definition
 */
@Serializable
data class ToolParameter(
    val type: String,
    val description: String? = null,
    val required: Boolean = false,
    val default: JsonElement? = null,
    val enum: List<String>? = null
)

/**
 * Tool definition according to MCP specification
 */
@Serializable
data class Tool(
    val name: String,
    val description: String,
    val inputSchema: ToolInputSchema
)

/**
 * Tool input schema definition
 */
@Serializable
data class ToolInputSchema(
    val type: String = "object",
    val properties: Map<String, ToolParameter> = emptyMap(),
    val required: List<String> = emptyList(),
    val additionalProperties: Boolean = false
)

/**
 * Tools list response result
 */
@Serializable
data class ToolsListResult(
    val tools: List<Tool>
)

/**
 * Tools list method handler for MCP protocol
 */
object ToolsListMethod {
    const val METHOD_NAME = "tools/list"
    
    /**
     * Handle tools/list request - returns available tools
     */
    suspend fun handle(request: MCPRequest): MCPResponse {
        return try {
            // Get tools from registry
            val tools = GlobalToolRegistry.instance.getToolDefinitions(includeUnavailable = false)
            
            val result = ToolsListResult(tools = tools)
            
            MCPResponse(
                id = request.id,
                result = MCPSerializers.json.encodeToJsonElement(result)
            )
            
        } catch (e: Exception) {
            MCPResponse(
                id = request.id,
                error = MCPError.internalError("Failed to list tools: ${e.message}")
            )
        }
    }
    
}