package de.jug_da.standapp.mcp.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/**
 * JSON serialization configuration for MCP protocol messages.
 * Provides polymorphic serialization for the MCPMessage sealed class hierarchy.
 */
object MCPSerializers {
    
    /**
     * Serializers module for MCP message polymorphism
     */
    private val mcpModule = SerializersModule {
        polymorphic(MCPMessage::class) {
            subclass(MCPRequest::class)
            subclass(MCPResponse::class)
            subclass(MCPNotification::class)
        }
    }
    
    /**
     * JSON configuration for MCP protocol serialization.
     * Configured with:
     * - Ignore unknown keys for forward compatibility
     * - Pretty printing disabled for performance
     * - Explicit nulls for protocol compliance
     * - Class discriminator for polymorphic serialization
     */
    val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
        allowSpecialFloatingPointValues = false
        allowStructuredMapKeys = true
        useArrayPolymorphism = false
        classDiscriminator = "jsonrpc"
        serializersModule = mcpModule
    }
    
    /**
     * Pretty-printed JSON configuration for debugging purposes
     */
    val prettyJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
        allowSpecialFloatingPointValues = false
        allowStructuredMapKeys = true
        useArrayPolymorphism = false
        classDiscriminator = "jsonrpc"
        serializersModule = mcpModule
    }
    
    /**
     * Lenient JSON configuration for parsing potentially malformed messages
     */
    val lenientJson = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
        allowSpecialFloatingPointValues = true
        allowStructuredMapKeys = true
        useArrayPolymorphism = false
        classDiscriminator = "jsonrpc"
        serializersModule = mcpModule
    }
}