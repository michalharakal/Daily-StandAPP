package de.jug_da.standapp.mcp.tools

import de.jug_da.standapp.mcp.methods.Tool
import de.jug_da.standapp.mcp.methods.ToolCallResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject

/**
 * Registry for managing MCP tools.
 * 
 * This class provides centralized management of all available MCP tools,
 * including registration, discovery, validation, and execution.
 * The registry is thread-safe and supports dynamic tool registration.
 */
class ToolRegistry {
    
    private val tools = mutableMapOf<String, MCPTool>()
    private val mutex = Mutex()
    
    /**
     * Register a new tool in the registry.
     * 
     * @param tool The tool to register
     * @throws IllegalArgumentException if a tool with the same name is already registered
     */
    suspend fun registerTool(tool: MCPTool) {
        mutex.withLock {
            if (tools.containsKey(tool.name)) {
                throw IllegalArgumentException("Tool with name '${tool.name}' is already registered")
            }
            tools[tool.name] = tool
        }
    }
    
    /**
     * Unregister a tool from the registry.
     * 
     * @param toolName Name of the tool to unregister
     * @return true if the tool was found and removed, false otherwise
     */
    suspend fun unregisterTool(toolName: String): Boolean {
        return mutex.withLock {
            tools.remove(toolName) != null
        }
    }
    
    /**
     * Get a tool by name.
     * 
     * @param toolName Name of the tool to retrieve
     * @return The tool if found, null otherwise
     */
    suspend fun getTool(toolName: String): MCPTool? {
        return mutex.withLock {
            tools[toolName]
        }
    }
    
    /**
     * Get all registered tools.
     * 
     * @return List of all registered tools
     */
    suspend fun getAllTools(): List<MCPTool> {
        return mutex.withLock {
            tools.values.toList()
        }
    }
    
    /**
     * Get all available tools (those that are currently available for execution).
     * 
     * @return List of available tools
     */
    suspend fun getAvailableTools(): List<MCPTool> {
        return mutex.withLock {
            tools.values.filter { it.isAvailable() }
        }
    }
    
    /**
     * Get tools by category.
     * 
     * @param category Category to filter by
     * @return List of tools in the specified category
     */
    suspend fun getToolsByCategory(category: String): List<MCPTool> {
        return mutex.withLock {
            tools.values.filter { it.category == category }
        }
    }
    
    /**
     * Get all tool definitions for MCP protocol.
     * This method returns the tool definitions that will be sent
     * in response to tools/list requests.
     * 
     * @param includeUnavailable Whether to include unavailable tools
     * @return List of tool definitions
     */
    suspend fun getToolDefinitions(includeUnavailable: Boolean = false): List<Tool> {
        return mutex.withLock {
            val toolsToInclude = if (includeUnavailable) {
                tools.values
            } else {
                tools.values.filter { it.isAvailable() }
            }
            toolsToInclude.map { it.getToolDefinition() }
        }
    }
    
    /**
     * Execute a tool by name with the provided arguments.
     * 
     * @param toolName Name of the tool to execute
     * @param arguments JSON object containing the tool arguments
     * @return ToolCallResult containing the execution result or error
     */
    suspend fun executeTool(toolName: String, arguments: JsonObject?): ToolCallResult {
        val tool = getTool(toolName)
            ?: return ToolCallResult(
                content = listOf(
                    de.jug_da.standapp.mcp.methods.ToolCallContent(
                        type = "text",
                        text = "Tool '$toolName' not found"
                    )
                ),
                isError = true
            )
        
        if (!tool.isAvailable()) {
            return ToolCallResult(
                content = listOf(
                    de.jug_da.standapp.mcp.methods.ToolCallContent(
                        type = "text",
                        text = "Tool '$toolName' is currently unavailable"
                    )
                ),
                isError = true
            )
        }
        
        // Validate arguments before execution
        val validationResult = tool.validateArguments(arguments)
        if (!validationResult.isValid) {
            val errorMessage = "Invalid arguments for tool '$toolName': ${validationResult.errors.joinToString(", ")}"
            return ToolCallResult(
                content = listOf(
                    de.jug_da.standapp.mcp.methods.ToolCallContent(
                        type = "text",
                        text = errorMessage
                    )
                ),
                isError = true
            )
        }
        
        return try {
            tool.execute(arguments)
        } catch (e: Exception) {
            ToolCallResult(
                content = listOf(
                    de.jug_da.standapp.mcp.methods.ToolCallContent(
                        type = "text",
                        text = "Error executing tool '$toolName': ${e.message}"
                    )
                ),
                isError = true
            )
        }
    }
    
    /**
     * Check if a tool exists in the registry.
     * 
     * @param toolName Name of the tool to check
     * @return true if the tool exists, false otherwise
     */
    suspend fun hasTool(toolName: String): Boolean {
        return mutex.withLock {
            tools.containsKey(toolName)
        }
    }
    
    /**
     * Get the number of registered tools.
     * 
     * @return Number of registered tools
     */
    suspend fun getToolCount(): Int {
        return mutex.withLock {
            tools.size
        }
    }
    
    /**
     * Clear all registered tools.
     * This method is primarily for testing purposes.
     */
    suspend fun clearAll() {
        mutex.withLock {
            tools.clear()
        }
    }
    
    /**
     * Get registry statistics.
     * 
     * @return Map containing registry statistics
     */
    suspend fun getStatistics(): Map<String, Any> {
        return mutex.withLock {
            val totalTools = tools.size
            val availableTools = tools.values.count { it.isAvailable() }
            val categoryCounts = tools.values.groupingBy { it.category }.eachCount()
            
            mapOf(
                "total_tools" to totalTools,
                "available_tools" to availableTools,
                "unavailable_tools" to (totalTools - availableTools),
                "categories" to categoryCounts
            )
        }
    }
    
    /**
     * Validate all registered tools.
     * This method checks that all tools are properly configured
     * and can provide valid tool definitions.
     * 
     * @return List of validation errors (empty if all tools are valid)
     */
    suspend fun validateAllTools(): List<String> {
        return mutex.withLock {
            val errors = mutableListOf<String>()
            
            tools.values.forEach { tool ->
                try {
                    // Check that tool can provide a valid definition
                    val definition = tool.getToolDefinition()
                    if (definition.name != tool.name) {
                        errors.add("Tool '${tool.name}' returns inconsistent name in definition: '${definition.name}'")
                    }
                    if (definition.description.isBlank()) {
                        errors.add("Tool '${tool.name}' has empty description")
                    }
                } catch (e: Exception) {
                    errors.add("Tool '${tool.name}' failed to provide definition: ${e.message}")
                }
            }
            
            errors
        }
    }
}

/**
 * Global instance of the tool registry.
 * This can be used across the application for tool management.
 */
object GlobalToolRegistry {
    val instance = ToolRegistry()
}