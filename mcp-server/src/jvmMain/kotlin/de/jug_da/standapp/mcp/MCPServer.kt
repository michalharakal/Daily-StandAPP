package de.jug_da.standapp.mcp

import de.jug_da.standapp.mcp.transport.KtorWebSocketTransport
import kotlinx.coroutines.*

/**
 * Main MCP Server application
 * Integrates all components to provide a complete Model Context Protocol server
 */
class MCPServer(
    private val host: String = "0.0.0.0",
    private val port: Int = 8080
) {
    private var transport: KtorWebSocketTransport? = null
    private val serverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Start the MCP server
     */
    fun start() {
        println("Starting Daily Stand App MCP Server...")
        println("Protocol Version: 2024-11-05")
        println("Available Tools: git_analysis, standup_summary, health_check")
        println("")
        
        transport = KtorWebSocketTransport(host, port, serverScope)
        transport?.start()
        
        println("Server is ready for connections!")
        println("WebSocket endpoint: ws://$host:$port/mcp")
        println("Use Ctrl+C to stop the server")
    }
    
    /**
     * Stop the MCP server
     */
    fun stop() {
        println("\nShutting down MCP Server...")
        transport?.stop()
        serverScope.cancel()
        println("Server stopped.")
    }
    
    /**
     * Get server status information
     */
    fun getStatus(): ServerStatus {
        val connectionCount = transport?.getConnectionCount() ?: 0
        val activeConnections = transport?.getActiveConnections() ?: emptySet()
        
        return ServerStatus(
            isRunning = transport != null,
            host = host,
            port = port,
            connectionCount = connectionCount,
            activeConnections = activeConnections.toList()
        )
    }
}

/**
 * Server status information
 */
data class ServerStatus(
    val isRunning: Boolean,
    val host: String,
    val port: Int,
    val connectionCount: Int,
    val activeConnections: List<String>
)

/**
 * Main application entry point
 */
fun main(args: Array<String>) {
    // Parse command line arguments
    var host = "0.0.0.0"
    var port = 8080
    
    args.forEachIndexed { index, arg ->
        when (arg) {
            "--host", "-h" -> {
                if (index + 1 < args.size) {
                    host = args[index + 1]
                }
            }
            "--port", "-p" -> {
                if (index + 1 < args.size) {
                    port = args[index + 1].toIntOrNull() ?: 8080
                }
            }
            "--help" -> {
                printUsage()
                return
            }
        }
    }
    
    val server = MCPServer(host, port)
    
    // Add shutdown hook for graceful shutdown
    Runtime.getRuntime().addShutdownHook(Thread {
        server.stop()
    })
    
    try {
        server.start()
        
        // Keep the server running
        runBlocking {
            while (true) {
                delay(1000)
            }
        }
    } catch (e: Exception) {
        println("Server error: ${e.message}")
        server.stop()
    }
}

/**
 * Print command line usage information
 */
private fun printUsage() {
    println("""
        Daily Stand App MCP Server
        
        Usage: java -jar mcp-server.jar [options]
        
        Options:
          --host, -h <host>    Server host address (default: 0.0.0.0)
          --port, -p <port>    Server port number (default: 8080)
          --help               Show this help message
        
        Example:
          java -jar mcp-server.jar --host localhost --port 9090
        
        WebSocket endpoint will be available at: ws://<host>:<port>/mcp
        
        Supported MCP methods:
          - initialize: Initialize the MCP session
          - tools/list: List available tools
          - tools/call: Execute a tool
          - ping: Health check
          
        Available tools:
          - git_analysis: Analyze Git repository commits
          - standup_summary: Generate AI-powered standup summaries
          - health_check: Check server health and status
    """.trimIndent())
}