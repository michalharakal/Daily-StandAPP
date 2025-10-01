package de.jug_da.standapp.mcp.config

import kotlinx.serialization.Serializable

/**
 * Configuration for the MCP Server
 * Supports multiple configuration sources: environment variables, config files, command-line args
 */
@Serializable
data class MCPServerConfig(
    val server: ServerConfig = ServerConfig(),
    val security: SecurityConfig = SecurityConfig(),
    val logging: LoggingConfig = LoggingConfig(),
    val git: GitConfig = GitConfig(),
    val llm: LLMConfig = LLMConfig(),
    val performance: PerformanceConfig = PerformanceConfig()
) {
    companion object {
        /**
         * Create configuration with default values
         */
        fun default(): MCPServerConfig = MCPServerConfig()
        
        /**
         * Validate the configuration and return list of validation errors
         */
        fun validate(config: MCPServerConfig): List<String> {
            val errors = mutableListOf<String>()
            
            // Validate server configuration
            if (config.server.port < 1 || config.server.port > 65535) {
                errors.add("Server port must be between 1 and 65535")
            }
            
            if (config.server.host.isBlank()) {
                errors.add("Server host cannot be empty")
            }
            
            // Validate timeout values
            if (config.server.requestTimeoutMs <= 0) {
                errors.add("Request timeout must be positive")
            }
            
            if (config.server.connectionTimeoutMs <= 0) {
                errors.add("Connection timeout must be positive")
            }
            
            // Validate Git configuration
            config.git.repositories.forEach { repo ->
                if (repo.path.isBlank()) {
                    errors.add("Repository path cannot be empty")
                }
                if (repo.name.isBlank()) {
                    errors.add("Repository name cannot be empty")
                }
            }
            
            // Validate LLM configuration
            if (config.llm.modelPath.isBlank()) {
                errors.add("LLM model path cannot be empty")
            }
            
            if (config.llm.maxTokens <= 0) {
                errors.add("LLM max tokens must be positive")
            }
            
            // Validate performance settings
            if (config.performance.maxConcurrentRequests <= 0) {
                errors.add("Max concurrent requests must be positive")
            }
            
            return errors
        }
    }
}

/**
 * Server-specific configuration
 */
@Serializable
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    val enableSsl: Boolean = false,
    val sslKeyStorePath: String = "",
    val sslKeyStorePassword: String = "",
    val requestTimeoutMs: Long = 30000,
    val connectionTimeoutMs: Long = 60000,
    val maxConnections: Int = 100,
    val enableCors: Boolean = true,
    val corsOrigins: List<String> = listOf("*")
)

/**
 * Security-related configuration
 */
@Serializable
data class SecurityConfig(
    val enableAuthentication: Boolean = false,
    val apiKeys: List<String> = emptyList(),
    val allowedIps: List<String> = emptyList(),
    val blockedIps: List<String> = emptyList(),
    val rateLimitEnabled: Boolean = false,
    val rateLimitRequestsPerMinute: Int = 60,
    val rateLimitBurstSize: Int = 10
)

/**
 * Logging configuration
 */
@Serializable
data class LoggingConfig(
    val level: LogLevel = LogLevel.INFO,
    val enableConsoleLogging: Boolean = true,
    val enableFileLogging: Boolean = false,
    val logFilePath: String = "mcp-server.log",
    val maxLogFileSize: String = "10MB",
    val maxLogFiles: Int = 5,
    val enableRequestLogging: Boolean = true,
    val logFormat: LogFormat = LogFormat.STRUCTURED
)

/**
 * Git-related configuration
 */
@Serializable
data class GitConfig(
    val repositories: List<RepositoryConfig> = emptyList(),
    val defaultBranch: String = "main",
    val maxCommitHistory: Int = 100,
    val enableCaching: Boolean = true,
    val cacheTimeoutMs: Long = 300000, // 5 minutes
    val credentialsProvider: String = "none" // none, file, environment
)

/**
 * Repository configuration
 */
@Serializable
data class RepositoryConfig(
    val name: String,
    val path: String,
    val enabled: Boolean = true,
    val readOnly: Boolean = true,
    val allowedBranches: List<String> = emptyList(),
    val excludedPaths: List<String> = emptyList()
)

/**
 * LLM service configuration
 */
@Serializable
data class LLMConfig(
    val modelPath: String = "",
    val maxTokens: Int = 2048,
    val temperature: Float = 0.7f,
    val enableGpu: Boolean = false,
    val threadCount: Int = 4,
    val contextLength: Int = 4096,
    val seed: Long = -1,
    val cacheEnabled: Boolean = true,
    val cacheSize: Int = 100
)

/**
 * Performance and resource configuration
 */
@Serializable
data class PerformanceConfig(
    val maxConcurrentRequests: Int = 10,
    val requestQueueSize: Int = 100,
    val enableCompression: Boolean = true,
    val compressionLevel: Int = 6,
    val enableMetrics: Boolean = true,
    val metricsPort: Int = 9090,
    val healthCheckInterval: Long = 30000,
    val gcInterval: Long = 60000
)

/**
 * Logging levels
 */
@Serializable
enum class LogLevel {
    TRACE, DEBUG, INFO, WARN, ERROR
}

/**
 * Log format options
 */
@Serializable
enum class LogFormat {
    PLAIN, STRUCTURED, JSON
}