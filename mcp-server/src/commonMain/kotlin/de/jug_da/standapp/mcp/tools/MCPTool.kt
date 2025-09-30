package de.jug_da.standapp.mcp.tools

import de.jug_da.standapp.mcp.methods.Tool
import de.jug_da.standapp.mcp.methods.ToolCallResult
import kotlinx.serialization.json.JsonObject

/**
 * Interface defining the contract for MCP tools.
 * 
 * This interface provides the foundation for all tools that can be exposed
 * through the Model Context Protocol (MCP) server. Each tool must provide
 * metadata about itself and implement the execution logic.
 */
interface MCPTool {
    
    /**
     * Unique identifier for this tool.
     * Must be unique across all registered tools in the registry.
     */
    val name: String
    
    /**
     * Human-readable description of what this tool does.
     * This will be shown to users and AI assistants to help them
     * understand when and how to use this tool.
     */
    val description: String
    
    /**
     * Version of this tool implementation.
     * Used for compatibility checking and debugging.
     */
    val version: String
        get() = "1.0.0"
    
    /**
     * Category or group this tool belongs to.
     * Used for organizing tools in listings and UI.
     */
    val category: String
        get() = "general"
    
    /**
     * Whether this tool requires special permissions or access.
     * Can be used by the registry for security checks.
     */
    val requiresPermissions: List<String>
        get() = emptyList()
    
    /**
     * Generate the tool definition for MCP protocol.
     * This method creates the Tool object that will be returned
     * in response to tools/list requests.
     * 
     * @return Tool definition including name, description, and input schema
     */
    fun getToolDefinition(): Tool
    
    /**
     * Execute the tool with the provided arguments.
     * This is the main implementation of the tool's functionality.
     * 
     * @param arguments JSON object containing the tool arguments
     * @return ToolCallResult containing the execution result or error
     */
    suspend fun execute(arguments: JsonObject?): ToolCallResult
    
    /**
     * Validate the provided arguments before execution.
     * This method should check if all required parameters are present
     * and have valid values.
     * 
     * @param arguments JSON object containing the tool arguments
     * @return ValidationResult indicating success or failure with details
     */
    fun validateArguments(arguments: JsonObject?): ValidationResult {
        return ValidationResult.success()
    }
    
    /**
     * Check if this tool is currently available for execution.
     * Can be used to disable tools based on system state,
     * permissions, or resource availability.
     * 
     * @return true if the tool can be executed, false otherwise
     */
    fun isAvailable(): Boolean = true
    
    /**
     * Get additional metadata about this tool.
     * Can include information like performance characteristics,
     * resource requirements, or usage statistics.
     * 
     * @return Map of metadata key-value pairs
     */
    fun getMetadata(): Map<String, Any> = emptyMap()
}

/**
 * Result of argument validation.
 */
data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
) {
    companion object {
        fun success() = ValidationResult(isValid = true)
        fun failure(vararg errors: String) = ValidationResult(isValid = false, errors = errors.toList())
        fun failure(errors: List<String>) = ValidationResult(isValid = false, errors = errors)
    }
}