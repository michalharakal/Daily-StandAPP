package de.jug_da.standapp.mcp.config

/**
 * Command-line argument parser for MCP Server
 * Supports various argument formats and provides help documentation
 */
class CommandLineParser {
    
    data class ParsedArgs(
        val configFile: String? = null,
        val host: String? = null,
        val port: Int? = null,
        val enableSsl: Boolean? = null,
        val logLevel: String? = null,
        val enableFileLogging: Boolean? = null,
        val logFile: String? = null,
        val modelPath: String? = null,
        val enableGpu: Boolean? = null,
        val maxConcurrentRequests: Int? = null,
        val enableMetrics: Boolean? = null,
        val metricsPort: Int? = null,
        val repositories: List<String> = emptyList(),
        val showHelp: Boolean = false,
        val showVersion: Boolean = false,
        val generateConfig: String? = null,
        val validateConfig: Boolean = false,
        val daemonMode: Boolean = false
    ) {
        /**
         * Convert parsed arguments to configuration map for ConfigLoader
         */
        fun toConfigMap(): Map<String, String> {
            val map = mutableMapOf<String, String>()
            
            host?.let { map["host"] = it }
            port?.let { map["port"] = it.toString() }
            enableSsl?.let { map["ssl"] = it.toString() }
            logLevel?.let { map["log-level"] = it }
            enableFileLogging?.let { map["file-logging"] = it.toString() }
            logFile?.let { map["log-file"] = it }
            modelPath?.let { map["model-path"] = it }
            enableGpu?.let { map["gpu"] = it.toString() }
            maxConcurrentRequests?.let { map["max-concurrent"] = it.toString() }
            enableMetrics?.let { map["metrics"] = it.toString() }
            metricsPort?.let { map["metrics-port"] = it.toString() }
            
            return map
        }
    }
    
    /**
     * Parse command-line arguments
     */
    fun parse(args: Array<String>): ParsedArgs {
        var result = ParsedArgs()
        val repositories = mutableListOf<String>()
        
        var i = 0
        while (i < args.size) {
            val arg = args[i]
            
            when {
                arg == "--help" || arg == "-h" -> {
                    result = result.copy(showHelp = true)
                }
                
                arg == "--version" || arg == "-v" -> {
                    result = result.copy(showVersion = true)
                }
                
                arg == "--config" || arg == "-c" -> {
                    if (i + 1 < args.size) {
                        result = result.copy(configFile = args[i + 1])
                        i++
                    } else {
                        throw ArgumentException("--config requires a file path")
                    }
                }
                
                arg == "--host" -> {
                    if (i + 1 < args.size) {
                        result = result.copy(host = args[i + 1])
                        i++
                    } else {
                        throw ArgumentException("--host requires a hostname")
                    }
                }
                
                arg == "--port" || arg == "-p" -> {
                    if (i + 1 < args.size) {
                        val port = args[i + 1].toIntOrNull()
                        if (port == null || port < 1 || port > 65535) {
                            throw ArgumentException("--port requires a valid port number (1-65535)")
                        }
                        result = result.copy(port = port)
                        i++
                    } else {
                        throw ArgumentException("--port requires a port number")
                    }
                }
                
                arg == "--ssl" -> {
                    result = result.copy(enableSsl = true)
                }
                
                arg == "--no-ssl" -> {
                    result = result.copy(enableSsl = false)
                }
                
                arg == "--log-level" -> {
                    if (i + 1 < args.size) {
                        val level = args[i + 1].uppercase()
                        if (level !in listOf("TRACE", "DEBUG", "INFO", "WARN", "ERROR")) {
                            throw ArgumentException("--log-level must be one of: TRACE, DEBUG, INFO, WARN, ERROR")
                        }
                        result = result.copy(logLevel = level)
                        i++
                    } else {
                        throw ArgumentException("--log-level requires a log level")
                    }
                }
                
                arg == "--log-file" -> {
                    if (i + 1 < args.size) {
                        result = result.copy(logFile = args[i + 1], enableFileLogging = true)
                        i++
                    } else {
                        throw ArgumentException("--log-file requires a file path")
                    }
                }
                
                arg == "--no-file-logging" -> {
                    result = result.copy(enableFileLogging = false)
                }
                
                arg == "--model-path" || arg == "-m" -> {
                    if (i + 1 < args.size) {
                        result = result.copy(modelPath = args[i + 1])
                        i++
                    } else {
                        throw ArgumentException("--model-path requires a file path")
                    }
                }
                
                arg == "--gpu" -> {
                    result = result.copy(enableGpu = true)
                }
                
                arg == "--no-gpu" -> {
                    result = result.copy(enableGpu = false)
                }
                
                arg == "--max-concurrent" -> {
                    if (i + 1 < args.size) {
                        val count = args[i + 1].toIntOrNull()
                        if (count == null || count < 1) {
                            throw ArgumentException("--max-concurrent requires a positive number")
                        }
                        result = result.copy(maxConcurrentRequests = count)
                        i++
                    } else {
                        throw ArgumentException("--max-concurrent requires a number")
                    }
                }
                
                arg == "--metrics" -> {
                    result = result.copy(enableMetrics = true)
                }
                
                arg == "--no-metrics" -> {
                    result = result.copy(enableMetrics = false)
                }
                
                arg == "--metrics-port" -> {
                    if (i + 1 < args.size) {
                        val port = args[i + 1].toIntOrNull()
                        if (port == null || port < 1 || port > 65535) {
                            throw ArgumentException("--metrics-port requires a valid port number (1-65535)")
                        }
                        result = result.copy(metricsPort = port)
                        i++
                    } else {
                        throw ArgumentException("--metrics-port requires a port number")
                    }
                }
                
                arg == "--repository" || arg == "-r" -> {
                    if (i + 1 < args.size) {
                        repositories.add(args[i + 1])
                        i++
                    } else {
                        throw ArgumentException("--repository requires a repository path")
                    }
                }
                
                arg == "--generate-config" -> {
                    if (i + 1 < args.size) {
                        result = result.copy(generateConfig = args[i + 1])
                        i++
                    } else {
                        throw ArgumentException("--generate-config requires a file path")
                    }
                }
                
                arg == "--validate-config" -> {
                    result = result.copy(validateConfig = true)
                }
                
                arg == "--daemon" || arg == "-d" -> {
                    result = result.copy(daemonMode = true)
                }
                
                arg.startsWith("--") -> {
                    throw ArgumentException("Unknown argument: $arg")
                }
                
                arg.startsWith("-") && arg.length > 2 -> {
                    // Handle short argument combinations like -vd
                    for (char in arg.drop(1)) {
                        when (char) {
                            'v' -> result = result.copy(showVersion = true)
                            'd' -> result = result.copy(daemonMode = true)
                            else -> throw ArgumentException("Unknown short argument: -$char")
                        }
                    }
                }
                
                else -> {
                    throw ArgumentException("Unexpected argument: $arg")
                }
            }
            
            i++
        }
        
        return result.copy(repositories = repositories)
    }
    
    /**
     * Print help information
     */
    fun printHelp() {
        println("""
            Daily Stand App MCP Server
            
            USAGE:
                mcp-server [OPTIONS]
            
            OPTIONS:
                -h, --help              Show this help message
                -v, --version           Show version information
                -c, --config <FILE>     Configuration file path (JSON/YAML)
                
            Server Options:
                --host <HOST>           Server host address (default: 0.0.0.0)
                -p, --port <PORT>       Server port number (default: 8080)
                --ssl                   Enable SSL/TLS
                --no-ssl                Disable SSL/TLS
                -d, --daemon            Run in daemon mode (background)
                
            Logging Options:
                --log-level <LEVEL>     Log level: TRACE, DEBUG, INFO, WARN, ERROR (default: INFO)
                --log-file <FILE>       Enable file logging with specified file path
                --no-file-logging       Disable file logging
                
            LLM Options:
                -m, --model-path <PATH> Path to LLM model file
                --gpu                   Enable GPU acceleration
                --no-gpu                Disable GPU acceleration
                
            Performance Options:
                --max-concurrent <N>    Maximum concurrent requests (default: 10)
                --metrics               Enable metrics collection
                --no-metrics            Disable metrics collection
                --metrics-port <PORT>   Metrics server port (default: 9090)
                
            Git Options:
                -r, --repository <PATH> Add Git repository path (can be used multiple times)
                
            Configuration Options:
                --generate-config <FILE> Generate sample configuration file
                --validate-config       Validate configuration and exit
                
            EXAMPLES:
                # Start server with default settings
                mcp-server
                
                # Start with custom host and port
                mcp-server --host localhost --port 9090
                
                # Start with configuration file
                mcp-server --config /path/to/config.json
                
                # Start with specific model and repositories
                mcp-server --model-path /path/to/model.ggml --repository /path/to/repo1 --repository /path/to/repo2
                
                # Generate sample configuration
                mcp-server --generate-config mcp-server.json
                
                # Start with debug logging
                mcp-server --log-level DEBUG --log-file debug.log
                
                # Start in daemon mode with SSL
                mcp-server --daemon --ssl --host 0.0.0.0 --port 443
            
            ENVIRONMENT VARIABLES:
                MCP_SERVER_HOST         Server host address
                MCP_SERVER_PORT         Server port number
                MCP_SERVER_SSL_ENABLED  Enable SSL (true/false)
                MCP_LOG_LEVEL           Log level
                MCP_LOG_FILE_PATH       Log file path
                MCP_LLM_MODEL_PATH      LLM model path
                MCP_LLM_GPU_ENABLED     Enable GPU (true/false)
                
                See documentation for complete list of environment variables.
                
            CONFIGURATION FILES:
                The server searches for configuration files in this order:
                1. File specified with --config
                2. ./mcp-server.json, ./mcp-server.yaml
                3. ./config/mcp-server.json, ./config/mcp-server.yaml  
                4. ~/.mcp/server.json, ~/.mcp/server.yaml
                5. /etc/mcp-server/config.json, /etc/mcp-server/config.yaml
                
                Configuration precedence: defaults < config file < environment variables < command line
                
            WEBSOCKET ENDPOINT:
                ws://<host>:<port>/mcp
                
            For more information, visit: https://github.com/your-org/daily-stand-app
        """.trimIndent())
    }
    
    /**
     * Print version information
     */
    fun printVersion() {
        println("Daily Stand App MCP Server")
        println("Version: 1.0.0")
        println("MCP Protocol Version: 2024-11-05")
        println("Build Date: ${java.time.LocalDate.now()}")
        println("JVM Version: ${System.getProperty("java.version")}")
        println("Kotlin Version: ${KotlinVersion.CURRENT}")
    }
    
    /**
     * Generate sample configuration file
     */
    fun generateSampleConfig(filePath: String) {
        val configLoader = ConfigLoader()
        val sampleConfig = MCPServerConfig(
            server = ServerConfig(
                host = "localhost",
                port = 8080,
                enableSsl = false,
                requestTimeoutMs = 30000,
                connectionTimeoutMs = 60000,
                maxConnections = 100
            ),
            security = SecurityConfig(
                enableAuthentication = false,
                rateLimitEnabled = true,
                rateLimitRequestsPerMinute = 60
            ),
            logging = LoggingConfig(
                level = LogLevel.INFO,
                enableConsoleLogging = true,
                enableFileLogging = true,
                logFilePath = "mcp-server.log"
            ),
            git = GitConfig(
                repositories = listOf(
                    RepositoryConfig(
                        name = "example-repo",
                        path = "/path/to/your/repository",
                        enabled = true,
                        readOnly = true
                    )
                ),
                defaultBranch = "main",
                maxCommitHistory = 100,
                enableCaching = true
            ),
            llm = LLMConfig(
                modelPath = "/path/to/your/model.ggml",
                maxTokens = 2048,
                temperature = 0.7f,
                enableGpu = false,
                threadCount = 4
            ),
            performance = PerformanceConfig(
                maxConcurrentRequests = 10,
                enableCompression = true,
                enableMetrics = true,
                metricsPort = 9090
            )
        )
        
        configLoader.saveToFile(sampleConfig, filePath)
        println("Sample configuration saved to: $filePath")
    }
}

/**
 * Exception for command-line argument parsing errors
 */
class ArgumentException(message: String) : Exception(message)