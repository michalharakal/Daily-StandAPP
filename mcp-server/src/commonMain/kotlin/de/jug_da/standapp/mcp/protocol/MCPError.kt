package de.jug_da.standapp.mcp.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Represents an error in MCP protocol communication.
 * Follows JSONRPC 2.0 error specification with MCP-specific error codes.
 */
@Serializable
data class MCPError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
) {
    companion object {
        // JSONRPC 2.0 standard error codes
        const val PARSE_ERROR = -32700
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INVALID_PARAMS = -32602
        const val INTERNAL_ERROR = -32603
        
        // MCP-specific error codes (server errors range: -32000 to -32099)
        const val TOOL_NOT_FOUND = -32000
        const val TOOL_EXECUTION_ERROR = -32001
        const val INITIALIZATION_ERROR = -32002
        const val UNSUPPORTED_OPERATION = -32003
        const val RESOURCE_NOT_FOUND = -32004
        const val PERMISSION_DENIED = -32005
        const val TIMEOUT_ERROR = -32006
        const val RATE_LIMIT_EXCEEDED = -32007
        const val CONFIGURATION_ERROR = -32008
        const val VALIDATION_ERROR = -32009
        
        /**
         * Create a parse error response
         */
        fun parseError(message: String = "Parse error", data: JsonElement? = null) =
            MCPError(PARSE_ERROR, message, data)
        
        /**
         * Create an invalid request error response
         */
        fun invalidRequest(message: String = "Invalid Request", data: JsonElement? = null) =
            MCPError(INVALID_REQUEST, message, data)
        
        /**
         * Create a method not found error response
         */
        fun methodNotFound(method: String, data: JsonElement? = null) =
            MCPError(METHOD_NOT_FOUND, "Method '$method' not found", data)
        
        /**
         * Create an invalid parameters error response
         */
        fun invalidParams(message: String = "Invalid params", data: JsonElement? = null) =
            MCPError(INVALID_PARAMS, message, data)
        
        /**
         * Create an internal error response
         */
        fun internalError(message: String = "Internal error", data: JsonElement? = null) =
            MCPError(INTERNAL_ERROR, message, data)
        
        /**
         * Create a tool not found error response
         */
        fun toolNotFound(toolName: String, data: JsonElement? = null) =
            MCPError(TOOL_NOT_FOUND, "Tool '$toolName' not found", data)
        
        /**
         * Create a tool execution error response
         */
        fun toolExecutionError(message: String, data: JsonElement? = null) =
            MCPError(TOOL_EXECUTION_ERROR, message, data)
        
        /**
         * Create an initialization error response
         */
        fun initializationError(message: String = "Server initialization failed", data: JsonElement? = null) =
            MCPError(INITIALIZATION_ERROR, message, data)
        
        /**
         * Create an unsupported operation error response
         */
        fun unsupportedOperation(operation: String, data: JsonElement? = null) =
            MCPError(UNSUPPORTED_OPERATION, "Operation '$operation' not supported", data)
        
        /**
         * Create a resource not found error response
         */
        fun resourceNotFound(resource: String, data: JsonElement? = null) =
            MCPError(RESOURCE_NOT_FOUND, "Resource '$resource' not found", data)
        
        /**
         * Create a permission denied error response
         */
        fun permissionDenied(message: String = "Permission denied", data: JsonElement? = null) =
            MCPError(PERMISSION_DENIED, message, data)
        
        /**
         * Create a timeout error response
         */
        fun timeoutError(message: String = "Operation timed out", data: JsonElement? = null) =
            MCPError(TIMEOUT_ERROR, message, data)
        
        /**
         * Create a rate limit exceeded error response
         */
        fun rateLimitExceeded(message: String = "Rate limit exceeded", data: JsonElement? = null) =
            MCPError(RATE_LIMIT_EXCEEDED, message, data)
        
        /**
         * Create a configuration error response
         */
        fun configurationError(message: String, data: JsonElement? = null) =
            MCPError(CONFIGURATION_ERROR, message, data)
        
        /**
         * Create a validation error response
         */
        fun validationError(message: String, data: JsonElement? = null) =
            MCPError(VALIDATION_ERROR, message, data)
    }
}