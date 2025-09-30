package de.jug_da.standapp.mcp.methods

import de.jug_da.standapp.mcp.protocol.MCPError
import de.jug_da.standapp.mcp.protocol.MCPRequest
import de.jug_da.standapp.mcp.protocol.MCPResponse
import de.jug_da.standapp.mcp.protocol.MCPSerializers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Ping response result
 */
@Serializable
data class PingResult(
    val message: String = "pong",
    val timestamp: Long = System.currentTimeMillis(),
    val serverInfo: PingServerInfo
)

/**
 * Server information included in ping response
 */
@Serializable
data class PingServerInfo(
    val name: String = "Daily Stand App MCP Server",
    val version: String = "1.0.0",
    val protocolVersion: String = "2024-11-05",
    val status: String = "healthy"
)

/**
 * Ping method handler for MCP protocol health checks
 */
object PingMethod {
    const val METHOD_NAME = "ping"
    
    /**
     * Handle ping request - returns server status and timestamp
     */
    fun handle(request: MCPRequest): MCPResponse {
        return try {
            val result = PingResult(
                serverInfo = PingServerInfo()
            )
            
            MCPResponse(
                id = request.id,
                result = MCPSerializers.json.encodeToJsonElement(result)
            )
            
        } catch (e: Exception) {
            MCPResponse(
                id = request.id,
                error = MCPError.internalError("Ping failed: ${e.message}")
            )
        }
    }
}