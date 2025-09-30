package de.jug_da.standapp.mcp.tools

/**
 * Tool registration utility for initializing all MCP tools.
 * 
 * This object provides centralized registration of all available
 * MCP tools in the global registry. It should be called during
 * server initialization to make all tools available.
 */
object ToolRegistration {
    
    /**
     * Register all available MCP tools in the global registry.
     * This should be called once during server startup.
     */
    suspend fun registerAllTools() {
        val registry = GlobalToolRegistry.instance
        
        // Register Git Analysis Tools
        registry.registerTool(GitAnalysisTool())
        
        // Register LLM-Powered Tools
        registry.registerTool(StandupSummaryTool())
        
        // TODO: Register additional tools as they are implemented
        // registry.registerTool(HealthCheckTool())
        // registry.registerTool(GitDiffTool())
        // registry.registerTool(GitBranchTool())
        // registry.registerTool(GitAuthorTool())
        // registry.registerTool(GitFilesTool())
        // registry.registerTool(GitMetricsTool())
    }
    
    /**
     * Get the list of tool names that should be registered.
     * Useful for validation and debugging.
     */
    fun getExpectedToolNames(): List<String> {
        return listOf(
            "git_analysis",
            "standup_summary"
            // Add more tool names as they are implemented
        )
    }
    
    /**
     * Verify that all expected tools are registered.
     * Returns a list of missing tool names.
     */
    suspend fun verifyRegistration(): List<String> {
        val registry = GlobalToolRegistry.instance
        val expectedTools = getExpectedToolNames()
        val missingTools = mutableListOf<String>()
        
        expectedTools.forEach { toolName ->
            if (!registry.hasTool(toolName)) {
                missingTools.add(toolName)
            }
        }
        
        return missingTools
    }
}