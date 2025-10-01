package de.jug_da.standapp.mcp.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Configuration loader that supports multiple formats and sources
 */
expect class ConfigLoader() {
    /**
     * Load configuration from a file
     * Supports JSON and YAML formats (determined by file extension)
     */
    fun loadFromFile(filePath: String): MCPServerConfig
    
    /**
     * Save configuration to a file
     * Format determined by file extension (.json, .yaml, .yml)
     */
    fun saveToFile(config: MCPServerConfig, filePath: String)
    
    /**
     * Load configuration from environment variables
     */
    fun loadFromEnvironment(): MCPServerConfig
    
    /**
     * Check if a configuration file exists
     */
    fun fileExists(filePath: String): Boolean
}

/**
 * Common configuration loading utilities
 */
object ConfigUtils {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * Parse JSON configuration
     */
    fun parseJson(jsonString: String): MCPServerConfig {
        return json.decodeFromString<MCPServerConfig>(jsonString)
    }
    
    /**
     * Serialize configuration to JSON
     */
    fun toJson(config: MCPServerConfig): String {
        return json.encodeToString(config)
    }
    
    /**
     * Merge two configurations, with override taking precedence
     */
    fun mergeConfigs(base: MCPServerConfig, override: MCPServerConfig): MCPServerConfig {
        return MCPServerConfig(
            server = mergeServerConfig(base.server, override.server),
            security = mergeSecurityConfig(base.security, override.security),
            logging = mergeLoggingConfig(base.logging, override.logging),
            git = mergeGitConfig(base.git, override.git),
            llm = mergeLLMConfig(base.llm, override.llm),
            performance = mergePerformanceConfig(base.performance, override.performance)
        )
    }
    
    private fun mergeServerConfig(base: ServerConfig, override: ServerConfig): ServerConfig {
        return ServerConfig(
            host = if (override.host != "0.0.0.0") override.host else base.host,
            port = if (override.port != 8080) override.port else base.port,
            enableSsl = override.enableSsl,
            sslKeyStorePath = override.sslKeyStorePath.ifEmpty { base.sslKeyStorePath },
            sslKeyStorePassword = override.sslKeyStorePassword.ifEmpty { base.sslKeyStorePassword },
            requestTimeoutMs = if (override.requestTimeoutMs != 30000L) override.requestTimeoutMs else base.requestTimeoutMs,
            connectionTimeoutMs = if (override.connectionTimeoutMs != 60000L) override.connectionTimeoutMs else base.connectionTimeoutMs,
            maxConnections = if (override.maxConnections != 100) override.maxConnections else base.maxConnections,
            enableCors = override.enableCors,
            corsOrigins = if (override.corsOrigins != listOf("*")) override.corsOrigins else base.corsOrigins
        )
    }
    
    private fun mergeSecurityConfig(base: SecurityConfig, override: SecurityConfig): SecurityConfig {
        return SecurityConfig(
            enableAuthentication = override.enableAuthentication,
            apiKeys = override.apiKeys.ifEmpty { base.apiKeys },
            allowedIps = override.allowedIps.ifEmpty { base.allowedIps },
            blockedIps = override.blockedIps.ifEmpty { base.blockedIps },
            rateLimitEnabled = override.rateLimitEnabled,
            rateLimitRequestsPerMinute = if (override.rateLimitRequestsPerMinute != 60) override.rateLimitRequestsPerMinute else base.rateLimitRequestsPerMinute,
            rateLimitBurstSize = if (override.rateLimitBurstSize != 10) override.rateLimitBurstSize else base.rateLimitBurstSize
        )
    }
    
    private fun mergeLoggingConfig(base: LoggingConfig, override: LoggingConfig): LoggingConfig {
        return LoggingConfig(
            level = override.level,
            enableConsoleLogging = override.enableConsoleLogging,
            enableFileLogging = override.enableFileLogging,
            logFilePath = if (override.logFilePath != "mcp-server.log") override.logFilePath else base.logFilePath,
            maxLogFileSize = if (override.maxLogFileSize != "10MB") override.maxLogFileSize else base.maxLogFileSize,
            maxLogFiles = if (override.maxLogFiles != 5) override.maxLogFiles else base.maxLogFiles,
            enableRequestLogging = override.enableRequestLogging,
            logFormat = override.logFormat
        )
    }
    
    private fun mergeGitConfig(base: GitConfig, override: GitConfig): GitConfig {
        return GitConfig(
            repositories = override.repositories.ifEmpty { base.repositories },
            defaultBranch = if (override.defaultBranch != "main") override.defaultBranch else base.defaultBranch,
            maxCommitHistory = if (override.maxCommitHistory != 100) override.maxCommitHistory else base.maxCommitHistory,
            enableCaching = override.enableCaching,
            cacheTimeoutMs = if (override.cacheTimeoutMs != 300000L) override.cacheTimeoutMs else base.cacheTimeoutMs,
            credentialsProvider = if (override.credentialsProvider != "none") override.credentialsProvider else base.credentialsProvider
        )
    }
    
    private fun mergeLLMConfig(base: LLMConfig, override: LLMConfig): LLMConfig {
        return LLMConfig(
            modelPath = override.modelPath.ifEmpty { base.modelPath },
            maxTokens = if (override.maxTokens != 2048) override.maxTokens else base.maxTokens,
            temperature = if (override.temperature != 0.7f) override.temperature else base.temperature,
            enableGpu = override.enableGpu,
            threadCount = if (override.threadCount != 4) override.threadCount else base.threadCount,
            contextLength = if (override.contextLength != 4096) override.contextLength else base.contextLength,
            seed = if (override.seed != -1L) override.seed else base.seed,
            cacheEnabled = override.cacheEnabled,
            cacheSize = if (override.cacheSize != 100) override.cacheSize else base.cacheSize
        )
    }
    
    private fun mergePerformanceConfig(base: PerformanceConfig, override: PerformanceConfig): PerformanceConfig {
        return PerformanceConfig(
            maxConcurrentRequests = if (override.maxConcurrentRequests != 10) override.maxConcurrentRequests else base.maxConcurrentRequests,
            requestQueueSize = if (override.requestQueueSize != 100) override.requestQueueSize else base.requestQueueSize,
            enableCompression = override.enableCompression,
            compressionLevel = if (override.compressionLevel != 6) override.compressionLevel else base.compressionLevel,
            enableMetrics = override.enableMetrics,
            metricsPort = if (override.metricsPort != 9090) override.metricsPort else base.metricsPort,
            healthCheckInterval = if (override.healthCheckInterval != 30000L) override.healthCheckInterval else base.healthCheckInterval,
            gcInterval = if (override.gcInterval != 60000L) override.gcInterval else base.gcInterval
        )
    }
    
    /**
     * Get standard configuration file paths to search
     */
    fun getConfigFilePaths(): List<String> {
        return listOf(
            "mcp-server.json",
            "mcp-server.yaml",
            "mcp-server.yml",
            "config/mcp-server.json",
            "config/mcp-server.yaml",
            "config/mcp-server.yml",
            System.getProperty("user.home") + "/.mcp/server.json",
            System.getProperty("user.home") + "/.mcp/server.yaml",
            "/etc/mcp-server/config.json",
            "/etc/mcp-server/config.yaml"
        )
    }
    
    /**
     * Environment variable mapping for configuration
     */
    fun getEnvironmentMapping(): Map<String, String> {
        return mapOf(
            "MCP_SERVER_HOST" to "server.host",
            "MCP_SERVER_PORT" to "server.port",
            "MCP_SERVER_SSL_ENABLED" to "server.enableSsl",
            "MCP_SERVER_SSL_KEYSTORE_PATH" to "server.sslKeyStorePath",
            "MCP_SERVER_SSL_KEYSTORE_PASSWORD" to "server.sslKeyStorePassword",
            "MCP_SERVER_REQUEST_TIMEOUT" to "server.requestTimeoutMs",
            "MCP_SERVER_CONNECTION_TIMEOUT" to "server.connectionTimeoutMs",
            "MCP_SERVER_MAX_CONNECTIONS" to "server.maxConnections",
            "MCP_SECURITY_AUTH_ENABLED" to "security.enableAuthentication",
            "MCP_SECURITY_API_KEYS" to "security.apiKeys",
            "MCP_SECURITY_ALLOWED_IPS" to "security.allowedIps",
            "MCP_SECURITY_BLOCKED_IPS" to "security.blockedIps",
            "MCP_SECURITY_RATE_LIMIT_ENABLED" to "security.rateLimitEnabled",
            "MCP_SECURITY_RATE_LIMIT_RPM" to "security.rateLimitRequestsPerMinute",
            "MCP_LOG_LEVEL" to "logging.level",
            "MCP_LOG_CONSOLE_ENABLED" to "logging.enableConsoleLogging",
            "MCP_LOG_FILE_ENABLED" to "logging.enableFileLogging",
            "MCP_LOG_FILE_PATH" to "logging.logFilePath",
            "MCP_GIT_DEFAULT_BRANCH" to "git.defaultBranch",
            "MCP_GIT_MAX_COMMIT_HISTORY" to "git.maxCommitHistory",
            "MCP_GIT_CACHE_ENABLED" to "git.enableCaching",
            "MCP_LLM_MODEL_PATH" to "llm.modelPath",
            "MCP_LLM_MAX_TOKENS" to "llm.maxTokens",
            "MCP_LLM_TEMPERATURE" to "llm.temperature",
            "MCP_LLM_GPU_ENABLED" to "llm.enableGpu",
            "MCP_LLM_THREAD_COUNT" to "llm.threadCount",
            "MCP_PERFORMANCE_MAX_CONCURRENT" to "performance.maxConcurrentRequests",
            "MCP_PERFORMANCE_QUEUE_SIZE" to "performance.requestQueueSize",
            "MCP_PERFORMANCE_COMPRESSION_ENABLED" to "performance.enableCompression",
            "MCP_PERFORMANCE_METRICS_ENABLED" to "performance.enableMetrics",
            "MCP_PERFORMANCE_METRICS_PORT" to "performance.metricsPort"
        )
    }
}