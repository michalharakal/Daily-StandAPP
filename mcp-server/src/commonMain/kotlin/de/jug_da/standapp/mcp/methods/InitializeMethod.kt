package de.jug_da.standapp.mcp.methods

import de.jug_da.standapp.mcp.protocol.MCPError
import de.jug_da.standapp.mcp.protocol.MCPRequest
import de.jug_da.standapp.mcp.protocol.MCPResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import de.jug_da.standapp.mcp.protocol.MCPSerializers

/**
 * Initialize request parameters according to MCP specification
 */
@Serializable
data class InitializeParams(
    val protocolVersion: String,
    val capabilities: ClientCapabilities,
    val clientInfo: ClientInfo
)

/**
 * Client capabilities structure
 */
@Serializable
data class ClientCapabilities(
    val roots: RootsCapability? = null,
    val sampling: SamplingCapability? = null
)

/**
 * Roots capability structure
 */
@Serializable
data class RootsCapability(
    val listChanged: Boolean? = null
)

/**
 * Sampling capability structure
 */
@Serializable
data class SamplingCapability(
    val supported: Boolean? = null
)

/**
 * Client information structure
 */
@Serializable
data class ClientInfo(
    val name: String,
    val version: String
)

/**
 * Initialize response result according to MCP specification
 */
@Serializable
data class InitializeResult(
    val protocolVersion: String,
    val capabilities: ServerCapabilities,
    val serverInfo: ServerInfo
)

/**
 * Server capabilities structure
 */
@Serializable
data class ServerCapabilities(
    val tools: ToolsCapability? = null,
    val resources: ResourcesCapability? = null,
    val prompts: PromptsCapability? = null,
    val logging: LoggingCapability? = null
)

/**
 * Tools capability structure
 */
@Serializable
data class ToolsCapability(
    val listChanged: Boolean? = null
)

/**
 * Resources capability structure
 */
@Serializable
data class ResourcesCapability(
    val subscribe: Boolean? = null,
    val listChanged: Boolean? = null
)

/**
 * Prompts capability structure
 */
@Serializable
data class PromptsCapability(
    val listChanged: Boolean? = null
)

/**
 * Logging capability structure
 */
@Serializable
data class LoggingCapability(
    // No specific fields defined in MCP spec yet
)

/**
 * Server information structure
 */
@Serializable
data class ServerInfo(
    val name: String,
    val version: String
)

/**
 * Initialize method handler for MCP protocol
 */
object InitializeMethod {
    const val METHOD_NAME = "initialize"
    const val PROTOCOL_VERSION = "2024-11-05"
    
    /**
     * Handle initialize request
     */
    fun handle(request: MCPRequest): MCPResponse {
        return try {
            // Parse initialize parameters
            val params = request.params?.let { paramsJson ->
                MCPSerializers.json.decodeFromJsonElement<InitializeParams>(paramsJson)
            } ?: return MCPResponse(
                id = request.id,
                error = MCPError.invalidParams("Missing initialize parameters")
            )
            
            // Validate protocol version compatibility
            if (!isProtocolVersionSupported(params.protocolVersion)) {
                return MCPResponse(
                    id = request.id,
                    error = MCPError.invalidParams("Unsupported protocol version: ${params.protocolVersion}")
                )
            }
            
            // Create server capabilities based on what we support
            val serverCapabilities = ServerCapabilities(
                tools = ToolsCapability(listChanged = false),
                resources = null, // Not implemented yet
                prompts = null,   // Not implemented yet
                logging = LoggingCapability()
            )
            
            // Create server info
            val serverInfo = ServerInfo(
                name = "Daily Stand App MCP Server",
                version = "1.0.0"
            )
            
            // Create initialize result
            val result = InitializeResult(
                protocolVersion = PROTOCOL_VERSION,
                capabilities = serverCapabilities,
                serverInfo = serverInfo
            )
            
            MCPResponse(
                id = request.id,
                result = MCPSerializers.json.encodeToJsonElement(result)
            )
            
        } catch (e: Exception) {
            MCPResponse(
                id = request.id,
                error = MCPError.internalError("Failed to process initialize request: ${e.message}")
            )
        }
    }
    
    /**
     * Check if the given protocol version is supported
     */
    private fun isProtocolVersionSupported(version: String): Boolean {
        // For now, we only support the current protocol version
        // In the future, this could be expanded to support multiple versions
        return version == PROTOCOL_VERSION
    }
}