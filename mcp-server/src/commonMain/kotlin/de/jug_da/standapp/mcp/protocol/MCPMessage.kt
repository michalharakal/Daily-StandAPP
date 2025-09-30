package de.jug_da.standapp.mcp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Sealed class hierarchy for Model Context Protocol (MCP) messages.
 * Represents all possible message types in the MCP communication protocol.
 */
@Serializable
sealed class MCPMessage {
    abstract val jsonrpc: String
    abstract val id: String?
}

/**
 * Base class for all MCP request messages.
 * Follows JSONRPC 2.0 specification for request format.
 */
@Serializable
data class MCPRequest(
    override val jsonrpc: String = "2.0",
    override val id: String,
    val method: String,
    val params: JsonElement? = null
) : MCPMessage()

/**
 * Base class for all MCP response messages.
 * Follows JSONRPC 2.0 specification for response format.
 */
@Serializable
data class MCPResponse(
    override val jsonrpc: String = "2.0",
    override val id: String?,
    val result: JsonElement? = null,
    val error: MCPError? = null
) : MCPMessage() {
    init {
        require((result != null) xor (error != null)) {
            "Response must have either result or error, but not both"
        }
    }
}

/**
 * Base class for MCP notification messages (requests without id).
 * Notifications do not expect a response.
 */
@Serializable
data class MCPNotification(
    override val jsonrpc: String = "2.0",
    override val id: String? = null,
    val method: String,
    val params: JsonElement? = null
) : MCPMessage()