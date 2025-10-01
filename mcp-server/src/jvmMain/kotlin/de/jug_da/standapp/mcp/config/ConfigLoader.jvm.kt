package de.jug_da.standapp.mcp.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * JVM implementation of ConfigLoader with file I/O capabilities
 */
actual class ConfigLoader {
    
    /**
     * Load configuration from a file
     * Supports JSON and YAML formats (determined by file extension)
     */
    actual fun loadFromFile(filePath: String): MCPServerConfig {
        val file = File(filePath)
        if (!file.exists()) {
            throw ConfigurationException("Configuration file not found: $filePath")
        }
        
        val content = file.readText()
        
        return when (file.extension.lowercase()) {
            "json" -> parseJson(content)
            "yaml", "yml" -> parseYaml(content)
            else -> throw ConfigurationException("Unsupported configuration file format: ${file.extension}")
        }
    }
    
    /**
     * Save configuration to a file
     * Format determined by file extension (.json, .yaml, .yml)
     */
    actual fun saveToFile(config: MCPServerConfig, filePath: String) {
        val file = File(filePath)
        file.parentFile?.mkdirs()
        
        val content = when (file.extension.lowercase()) {
            "json" -> ConfigUtils.toJson(config)
            "yaml", "yml" -> toYaml(config)
            else -> throw ConfigurationException("Unsupported configuration file format: ${file.extension}")
        }
        
        file.writeText(content)
    }
    
    /**
     * Load configuration from environment variables
     */
    actual fun loadFromEnvironment(): MCPServerConfig {
        val envMapping = ConfigUtils.getEnvironmentMapping()
        var config = MCPServerConfig.default()
        
        envMapping.forEach { (envVar, configPath) ->
            val envValue = System.getenv(envVar)
            if (envValue != null) {
                config = setConfigValue(config, configPath, envValue)
            }
        }
        
        return config
    }
    
    /**
     * Check if a configuration file exists
     */
    actual fun fileExists(filePath: String): Boolean {
        return File(filePath).exists()
    }
    
    /**
     * Load configuration with precedence: defaults < config file < environment variables < command line args
     */
    fun loadConfiguration(
        configFile: String? = null,
        commandLineArgs: Map<String, String> = emptyMap()
    ): MCPServerConfig {
        var config = MCPServerConfig.default()
        
        // 1. Try to load from configuration file
        val actualConfigFile = configFile ?: findConfigFile()
        if (actualConfigFile != null && fileExists(actualConfigFile)) {
            try {
                val fileConfig = loadFromFile(actualConfigFile)
                config = ConfigUtils.mergeConfigs(config, fileConfig)
            } catch (e: Exception) {
                throw ConfigurationException("Failed to load configuration from $actualConfigFile: ${e.message}", e)
            }
        }
        
        // 2. Override with environment variables
        try {
            val envConfig = loadFromEnvironment()
            config = ConfigUtils.mergeConfigs(config, envConfig)
        } catch (e: Exception) {
            throw ConfigurationException("Failed to load environment configuration: ${e.message}", e)
        }
        
        // 3. Override with command line arguments
        commandLineArgs.forEach { (key, value) ->
            config = setConfigValueFromCommandLine(config, key, value)
        }
        
        // 4. Validate final configuration
        val validationErrors = MCPServerConfig.validate(config)
        if (validationErrors.isNotEmpty()) {
            throw ConfigurationException("Configuration validation failed: ${validationErrors.joinToString(", ")}")
        }
        
        return config
    }
    
    /**
     * Find the first existing configuration file from standard paths
     */
    private fun findConfigFile(): String? {
        return ConfigUtils.getConfigFilePaths().firstOrNull { fileExists(it) }
    }
    
    /**
     * Parse JSON configuration
     */
    private fun parseJson(jsonString: String): MCPServerConfig {
        return ConfigUtils.parseJson(jsonString)
    }
    
    /**
     * Parse YAML configuration (simplified implementation)
     * In a real implementation, you would use a YAML library like SnakeYAML
     */
    private fun parseYaml(yamlString: String): MCPServerConfig {
        // For now, convert simple YAML to JSON and parse
        // This is a simplified implementation - in production use proper YAML parser
        val jsonString = convertSimpleYamlToJson(yamlString)
        return parseJson(jsonString)
    }
    
    /**
     * Convert configuration to YAML format (simplified)
     */
    private fun toYaml(config: MCPServerConfig): String {
        // Simplified YAML output - in production use proper YAML library
        return convertJsonToSimpleYaml(ConfigUtils.toJson(config))
    }
    
    /**
     * Simple YAML to JSON conversion (basic implementation)
     * In production, use a proper YAML library like SnakeYAML
     */
    private fun convertSimpleYamlToJson(yaml: String): String {
        // This is a very basic implementation for demonstration
        // For production use, integrate with a proper YAML parser
        val lines = yaml.lines().filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        val json = StringBuilder("{\n")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains(":") && !trimmed.startsWith("-")) {
                val parts = trimmed.split(":", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    json.append("  \"$key\": ")
                    
                    when {
                        value.startsWith("\"") && value.endsWith("\"") -> json.append(value)
                        value.equals("true", ignoreCase = true) -> json.append("true")
                        value.equals("false", ignoreCase = true) -> json.append("false")
                        value.toIntOrNull() != null -> json.append(value)
                        value.toDoubleOrNull() != null -> json.append(value)
                        else -> json.append("\"$value\"")
                    }
                    json.append(",\n")
                }
            }
        }
        
        if (json.endsWith(",\n")) {
            json.setLength(json.length - 2)
            json.append("\n")
        }
        json.append("}")
        
        return json.toString()
    }
    
    /**
     * Simple JSON to YAML conversion (basic implementation)
     */
    private fun convertJsonToSimpleYaml(json: String): String {
        // Basic JSON to YAML conversion for demonstration
        // In production, use proper YAML library
        return json.replace("{", "")
            .replace("}", "")
            .replace("\"", "")
            .replace(",", "")
            .lines()
            .filter { it.isNotBlank() }
            .joinToString("\n") { line ->
                val trimmed = line.trim()
                if (trimmed.contains(":")) {
                    val parts = trimmed.split(":", limit = 2)
                    if (parts.size == 2) {
                        "${parts[0].trim()}: ${parts[1].trim()}"
                    } else {
                        trimmed
                    }
                } else {
                    trimmed
                }
            }
    }
    
    /**
     * Set configuration value based on dot-notation path
     */
    private fun setConfigValue(config: MCPServerConfig, path: String, value: String): MCPServerConfig {
        val parts = path.split(".")
        
        return when (parts[0]) {
            "server" -> config.copy(server = setServerConfigValue(config.server, parts.drop(1), value))
            "security" -> config.copy(security = setSecurityConfigValue(config.security, parts.drop(1), value))
            "logging" -> config.copy(logging = setLoggingConfigValue(config.logging, parts.drop(1), value))
            "git" -> config.copy(git = setGitConfigValue(config.git, parts.drop(1), value))
            "llm" -> config.copy(llm = setLLMConfigValue(config.llm, parts.drop(1), value))
            "performance" -> config.copy(performance = setPerformanceConfigValue(config.performance, parts.drop(1), value))
            else -> config
        }
    }
    
    /**
     * Set configuration value from command line argument
     */
    private fun setConfigValueFromCommandLine(config: MCPServerConfig, key: String, value: String): MCPServerConfig {
        return when (key) {
            "host", "h" -> config.copy(server = config.server.copy(host = value))
            "port", "p" -> config.copy(server = config.server.copy(port = value.toIntOrNull() ?: config.server.port))
            "ssl" -> config.copy(server = config.server.copy(enableSsl = value.toBoolean()))
            "log-level" -> config.copy(logging = config.logging.copy(level = LogLevel.valueOf(value.uppercase())))
            "model-path", "m" -> config.copy(llm = config.llm.copy(modelPath = value))
            else -> config
        }
    }
    
    private fun setServerConfigValue(server: ServerConfig, path: List<String>, value: String): ServerConfig {
        if (path.isEmpty()) return server
        
        return when (path[0]) {
            "host" -> server.copy(host = value)
            "port" -> server.copy(port = value.toIntOrNull() ?: server.port)
            "enableSsl" -> server.copy(enableSsl = value.toBoolean())
            "sslKeyStorePath" -> server.copy(sslKeyStorePath = value)
            "sslKeyStorePassword" -> server.copy(sslKeyStorePassword = value)
            "requestTimeoutMs" -> server.copy(requestTimeoutMs = value.toLongOrNull() ?: server.requestTimeoutMs)
            "connectionTimeoutMs" -> server.copy(connectionTimeoutMs = value.toLongOrNull() ?: server.connectionTimeoutMs)
            "maxConnections" -> server.copy(maxConnections = value.toIntOrNull() ?: server.maxConnections)
            "enableCors" -> server.copy(enableCors = value.toBoolean())
            else -> server
        }
    }
    
    private fun setSecurityConfigValue(security: SecurityConfig, path: List<String>, value: String): SecurityConfig {
        if (path.isEmpty()) return security
        
        return when (path[0]) {
            "enableAuthentication" -> security.copy(enableAuthentication = value.toBoolean())
            "apiKeys" -> security.copy(apiKeys = value.split(",").map { it.trim() })
            "allowedIps" -> security.copy(allowedIps = value.split(",").map { it.trim() })
            "blockedIps" -> security.copy(blockedIps = value.split(",").map { it.trim() })
            "rateLimitEnabled" -> security.copy(rateLimitEnabled = value.toBoolean())
            "rateLimitRequestsPerMinute" -> security.copy(rateLimitRequestsPerMinute = value.toIntOrNull() ?: security.rateLimitRequestsPerMinute)
            else -> security
        }
    }
    
    private fun setLoggingConfigValue(logging: LoggingConfig, path: List<String>, value: String): LoggingConfig {
        if (path.isEmpty()) return logging
        
        return when (path[0]) {
            "level" -> logging.copy(level = LogLevel.valueOf(value.uppercase()))
            "enableConsoleLogging" -> logging.copy(enableConsoleLogging = value.toBoolean())
            "enableFileLogging" -> logging.copy(enableFileLogging = value.toBoolean())
            "logFilePath" -> logging.copy(logFilePath = value)
            else -> logging
        }
    }
    
    private fun setGitConfigValue(git: GitConfig, path: List<String>, value: String): GitConfig {
        if (path.isEmpty()) return git
        
        return when (path[0]) {
            "defaultBranch" -> git.copy(defaultBranch = value)
            "maxCommitHistory" -> git.copy(maxCommitHistory = value.toIntOrNull() ?: git.maxCommitHistory)
            "enableCaching" -> git.copy(enableCaching = value.toBoolean())
            else -> git
        }
    }
    
    private fun setLLMConfigValue(llm: LLMConfig, path: List<String>, value: String): LLMConfig {
        if (path.isEmpty()) return llm
        
        return when (path[0]) {
            "modelPath" -> llm.copy(modelPath = value)
            "maxTokens" -> llm.copy(maxTokens = value.toIntOrNull() ?: llm.maxTokens)
            "temperature" -> llm.copy(temperature = value.toFloatOrNull() ?: llm.temperature)
            "enableGpu" -> llm.copy(enableGpu = value.toBoolean())
            "threadCount" -> llm.copy(threadCount = value.toIntOrNull() ?: llm.threadCount)
            else -> llm
        }
    }
    
    private fun setPerformanceConfigValue(performance: PerformanceConfig, path: List<String>, value: String): PerformanceConfig {
        if (path.isEmpty()) return performance
        
        return when (path[0]) {
            "maxConcurrentRequests" -> performance.copy(maxConcurrentRequests = value.toIntOrNull() ?: performance.maxConcurrentRequests)
            "requestQueueSize" -> performance.copy(requestQueueSize = value.toIntOrNull() ?: performance.requestQueueSize)
            "enableCompression" -> performance.copy(enableCompression = value.toBoolean())
            "enableMetrics" -> performance.copy(enableMetrics = value.toBoolean())
            "metricsPort" -> performance.copy(metricsPort = value.toIntOrNull() ?: performance.metricsPort)
            else -> performance
        }
    }
}

/**
 * Configuration-related exceptions
 */
class ConfigurationException(message: String, cause: Throwable? = null) : Exception(message, cause)