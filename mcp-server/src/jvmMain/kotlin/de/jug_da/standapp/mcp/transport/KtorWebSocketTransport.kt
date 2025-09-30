package de.jug_da.standapp.mcp.transport

import de.jug_da.standapp.mcp.methods.*
import de.jug_da.standapp.mcp.protocol.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * WebSocket transport implementation using Ktor for MCP protocol
 */
class KtorWebSocketTransport(
    private val host: String = "0.0.0.0",
    private val port: Int = 8080,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private var server: ApplicationEngine? = null
    private val connections = ConcurrentHashMap<String, WebSocketSession>()
    private val connectionIdCounter = AtomicLong(0)
    private val requestHandlers = createRequestHandlers()
    
    /**
     * Start the WebSocket server
     */
    fun start() {
        server = embeddedServer(Netty, port = port, host = host) {
            configureWebSockets()
            configureCORS()
            configureRouting()
        }.start(wait = false)
        
        println("MCP Server started on ws://$host:$port/mcp")
    }
    
    /**
     * Stop the WebSocket server
     */
    fun stop() {
        server?.stop(1000, 2000)
        coroutineScope.cancel()
        connections.clear()
    }
    
    /**
     * Configure WebSocket support
     */
    private fun Application.configureWebSockets() {
        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(30)
            timeout = Duration.ofSeconds(60)
            maxFrameSize = Long.MAX_VALUE
            masking = false
        }
    }
    
    /**
     * Configure CORS for web clients
     */
    private fun Application.configureCORS() {
        install(CORS) {
            anyHost()
            allowMethod(io.ktor.http.HttpMethod.Get)
            allowMethod(io.ktor.http.HttpMethod.Post)
            allowMethod(io.ktor.http.HttpMethod.Options)
            allowHeader(io.ktor.http.HttpHeaders.ContentType)
            allowHeader(io.ktor.http.HttpHeaders.Authorization)
            allowCredentials = true
        }
    }
    
    /**
     * Configure routing with WebSocket endpoint
     */
    private fun Application.configureRouting() {
        routing {
            webSocket("/mcp") {
                handleWebSocketConnection(this)
            }
        }
    }
    
    /**
     * Handle WebSocket connection lifecycle
     */
    private suspend fun handleWebSocketConnection(session: WebSocketSession) {
        val connectionId = "conn_${connectionIdCounter.incrementAndGet()}"
        connections[connectionId] = session
        
        println("Client connected: $connectionId")
        
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> {
                        handleTextFrame(session, connectionId, frame.readText())
                    }
                    is Frame.Close -> {
                        println("Client $connectionId closed connection: ${frame.readReason()}")
                        break
                    }
                    else -> {
                        // Ignore other frame types (Binary, Ping, Pong)
                    }
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            println("Client $connectionId disconnected")
        } catch (e: Exception) {
            println("Error handling connection $connectionId: ${e.message}")
            val errorResponse = MCPResponse(
                id = null,
                error = MCPError.internalError("Connection error: ${e.message}")
            )
            sendResponse(session, errorResponse)
        } finally {
            connections.remove(connectionId)
            println("Client $connectionId cleaned up")
        }
    }
    
    /**
     * Handle incoming text frame (JSON-RPC message)
     */
    private suspend fun handleTextFrame(session: WebSocketSession, connectionId: String, text: String) {
        try {
            // Parse JSON
            val json = MCPSerializers.json.parseToJsonElement(text)
            
            // Validate JSON-RPC format
            val validation = MCPValidator.validateRequest(json)
            if (!validation.isValid) {
                val errorResponse = MCPResponse(
                    id = null,
                    error = MCPError.invalidRequest(
                        (validation as ValidationResult.Invalid).reason
                    )
                )
                sendResponse(session, errorResponse)
                return
            }
            
            // Deserialize to MCPRequest
            val request = try {
                MCPSerializers.json.decodeFromJsonElement<MCPRequest>(json)
            } catch (e: Exception) {
                val errorResponse = MCPResponse(
                    id = null,
                    error = MCPError.parseError("Failed to parse request: ${e.message}")
                )
                sendResponse(session, errorResponse)
                return
            }
            
            // Process request
            processRequest(session, connectionId, request)
            
        } catch (e: Exception) {
            println("Error processing message from $connectionId: ${e.message}")
            val errorResponse = MCPResponse(
                id = null,
                error = MCPError.internalError("Message processing failed: ${e.message}")
            )
            sendResponse(session, errorResponse)
        }
    }
    
    /**
     * Process MCP request and send response
     */
    private suspend fun processRequest(session: WebSocketSession, connectionId: String, request: MCPRequest) {
        println("Processing ${request.method} from $connectionId (id: ${request.id})")
        
        val response = try {
            val handler = requestHandlers[request.method]
            if (handler != null) {
                handler(request)
            } else {
                MCPResponse(
                    id = request.id,
                    error = MCPError.methodNotFound(request.method)
                )
            }
        } catch (e: Exception) {
            MCPResponse(
                id = request.id,
                error = MCPError.internalError("Request processing failed: ${e.message}")
            )
        }
        
        sendResponse(session, response)
    }
    
    /**
     * Send response to client
     */
    private suspend fun sendResponse(session: WebSocketSession, response: MCPResponse) {
        try {
            val responseJson = MCPSerializers.json.encodeToJsonElement(response)
            val responseText = MCPSerializers.json.encodeToString(MCPResponse.serializer(), response)
            session.send(Frame.Text(responseText))
        } catch (e: Exception) {
            println("Error sending response: ${e.message}")
        }
    }
    
    /**
     * Create request handlers map
     */
    private fun createRequestHandlers(): Map<String, (MCPRequest) -> MCPResponse> {
        return mapOf(
            InitializeMethod.METHOD_NAME to InitializeMethod::handle,
            ToolsListMethod.METHOD_NAME to ToolsListMethod::handle,
            ToolsCallMethod.METHOD_NAME to ToolsCallMethod::handle,
            PingMethod.METHOD_NAME to PingMethod::handle
        )
    }
    
    /**
     * Get current connection count
     */
    fun getConnectionCount(): Int = connections.size
    
    /**
     * Get all active connection IDs
     */
    fun getActiveConnections(): Set<String> = connections.keys.toSet()
    
    /**
     * Send a notification to all connected clients
     */
    suspend fun broadcastNotification(notification: MCPNotification) {
        val notificationText = MCPSerializers.json.encodeToString(MCPNotification.serializer(), notification)
        connections.values.forEach { session ->
            try {
                session.send(Frame.Text(notificationText))
            } catch (e: Exception) {
                println("Error broadcasting notification: ${e.message}")
            }
        }
    }
}