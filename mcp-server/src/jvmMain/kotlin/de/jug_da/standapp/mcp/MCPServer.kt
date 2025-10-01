package de.jug_da.standapp.mcp

import de.jug_da.standapp.mcp.config.*
import de.jug_da.standapp.mcp.transport.KtorWebSocketTransport
import kotlinx.coroutines.*

/**
 * Main MCP Server application
 * Integrates all components to provide a complete Model Context Protocol server
 */
class MCPServer(
    private val config: MCPServerConfig
) {
    private var transport: KtorWebSocketTransport? = null
    private val serverScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    /**
     * Start the MCP server
     */
    fun start() {
        if (config.logging.enableConsoleLogging) {
            println("Starting Daily Stand App MCP Server...")
            println("Protocol Version: 2024-11-05")
            println("Available Tools: git_analysis, standup_summary, health_check")
            println("Configuration: ${if (config.logging.level == LogLevel.DEBUG) "Debug mode enabled" else "Production mode"}")
            println("")
        }
        
        transport = KtorWebSocketTransport(config.server.host, config.server.port, serverScope)
        transport?.start()
        
        if (config.logging.enableConsoleLogging) {
            println("Server is ready for connections!")
            println("WebSocket endpoint: ws://${config.server.host}:${config.server.port}/mcp")
            if (config.server.enableSsl) {
                println("SSL/TLS: Enabled")
            }
            if (config.performance.enableMetrics) {
                println("Metrics endpoint: http://${config.server.host}:${config.performance.metricsPort}/metrics")
            }
            println("Use Ctrl+C to stop the server")
        }
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
            host = config.server.host,
            port = config.server.port,
            connectionCount = connectionCount,
            activeConnections = activeConnections.toList(),
            sslEnabled = config.server.enableSsl,
            metricsEnabled = config.performance.enableMetrics,
            metricsPort = config.performance.metricsPort
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
    val activeConnections: List<String>,
    val sslEnabled: Boolean = false,
    val metricsEnabled: Boolean = false,
    val metricsPort: Int = 9090
)

/**
 * Main application entry point
 */
fun main(args: Array<String>) {
    val parser = CommandLineParser()
    
    try {
        // Parse command line arguments
        val parsedArgs = parser.parse(args)
        
        // Handle special commands first
        when {
            parsedArgs.showHelp -> {
                parser.printHelp()
                return
            }
            
            parsedArgs.showVersion -> {
                parser.printVersion()
                return
            }
            
            parsedArgs.generateConfig != null -> {
                parser.generateSampleConfig(parsedArgs.generateConfig)
                return
            }
            
            parsedArgs.validateConfig -> {
                validateConfigurationAndExit(parsedArgs.configFile, parsedArgs.toConfigMap())
                return
            }
        }
        
        // Load configuration with proper precedence
        val configLoader = ConfigLoader()
        val config = configLoader.loadConfiguration(
            configFile = parsedArgs.configFile,
            commandLineArgs = parsedArgs.toConfigMap()
        )
        
        // Additional validation for runtime requirements
        validateRuntimeRequirements(config)
        
        // Create and start server
        val server = MCPServer(config)
        
        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(Thread {
            if (config.logging.enableConsoleLogging) {
                println("\nReceived shutdown signal...")
            }
            server.stop()
        })
        
        // Start the server
        server.start()
        
        // Handle daemon mode
        if (parsedArgs.daemonMode) {
            if (config.logging.enableConsoleLogging) {
                println("Running in daemon mode...")
            }
            // In a real implementation, you would detach from the terminal here
        }
        
        // Keep the server running
        runBlocking {
            while (true) {
                delay(1000)
                
                // Optional: periodic health checks or maintenance tasks
                if (config.performance.healthCheckInterval > 0) {
                    // Perform health checks every N seconds
                }
            }
        }
        
    } catch (e: ArgumentException) {
        System.err.println("Argument error: ${e.message}")
        System.err.println("Use --help for usage information")
        kotlin.system.exitProcess(1)
        
    } catch (e: ConfigurationException) {
        System.err.println("Configuration error: ${e.message}")
        kotlin.system.exitProcess(2)
        
    } catch (e: Exception) {
        System.err.println("Server error: ${e.message}")
        if (System.getenv("MCP_DEBUG") == "true") {
            e.printStackTrace()
        }
        kotlin.system.exitProcess(3)
    }
}

/**
 * Validate configuration file and exit
 */
private fun validateConfigurationAndExit(configFile: String?, commandLineArgs: Map<String, String>) {
    try {
        val configLoader = ConfigLoader()
        val config = configLoader.loadConfiguration(
            configFile = configFile,
            commandLineArgs = commandLineArgs
        )
        
        val validationErrors = MCPServerConfig.validate(config)
        if (validationErrors.isEmpty()) {
            println("✓ Configuration is valid")
            kotlin.system.exitProcess(0)
        } else {
            System.err.println("✗ Configuration validation failed:")
            validationErrors.forEach { error ->
                System.err.println("  - $error")
            }
            kotlin.system.exitProcess(1)
        }
    } catch (e: Exception) {
        System.err.println("✗ Failed to load configuration: ${e.message}")
        kotlin.system.exitProcess(1)
    }
}

/**
 * Validate runtime requirements
 */
private fun validateRuntimeRequirements(config: MCPServerConfig) {
    val errors = mutableListOf<String>()
    
    // Check if LLM model file exists if specified
    if (config.llm.modelPath.isNotEmpty()) {
        val modelFile = java.io.File(config.llm.modelPath)
        if (!modelFile.exists()) {
            errors.add("LLM model file not found: ${config.llm.modelPath}")
        }
    }
    
    // Check Git repositories
    config.git.repositories.forEach { repo ->
        val repoDir = java.io.File(repo.path)
        if (!repoDir.exists()) {
            errors.add("Git repository not found: ${repo.path}")
        } else if (!java.io.File(repoDir, ".git").exists()) {
            errors.add("Directory is not a Git repository: ${repo.path}")
        }
    }
    
    // Check if log directory is writable if file logging is enabled
    if (config.logging.enableFileLogging) {
        val logFile = java.io.File(config.logging.logFilePath)
        val logDir = logFile.parentFile ?: java.io.File(".")
        if (!logDir.exists() && !logDir.mkdirs()) {
            errors.add("Cannot create log directory: ${logDir.absolutePath}")
        } else if (!logDir.canWrite()) {
            errors.add("Log directory is not writable: ${logDir.absolutePath}")
        }
    }
    
    // Check port availability (basic check)
    if (isPortInUse(config.server.host, config.server.port)) {
        errors.add("Port ${config.server.port} is already in use on ${config.server.host}")
    }
    
    if (config.performance.enableMetrics && isPortInUse(config.server.host, config.performance.metricsPort)) {
        errors.add("Metrics port ${config.performance.metricsPort} is already in use on ${config.server.host}")
    }
    
    if (errors.isNotEmpty()) {
        throw ConfigurationException("Runtime validation failed: ${errors.joinToString(", ")}")
    }
}

/**
 * Check if a port is in use (basic implementation)
 */
private fun isPortInUse(host: String, port: Int): Boolean {
    return try {
        java.net.Socket(host, port).use { true }
    } catch (e: Exception) {
        false
    }
}
