package de.jug_da.standapp.mcp.tools

import kotlinx.serialization.json.*

object SchemaUtils {
    
    fun getCommitsByAuthorInputSchema(): JsonObject = buildJsonObject {
        putJsonObject("repoDir") {
            put("type", "string")
            put("description", "Path to the Git repository directory")
        }
        putJsonObject("author") {
            put("type", "string")
            put("description", "Author name to filter commits by")
        }
        putJsonObject("startDate") {
            put("type", "string")
            put("description", "Start date in ISO format (e.g., 2024-01-01T00:00:00Z)")
        }
        putJsonObject("endDate") {
            put("type", "string")
            put("description", "End date in ISO format (e.g., 2024-12-31T23:59:59Z)")
        }
    }
    
    fun getAllCommitsInputSchema(): JsonObject = buildJsonObject {
        putJsonObject("repoDir") {
            put("type", "string")
            put("description", "Path to the Git repository directory")
        }
        putJsonObject("startDate") {
            put("type", "string")
            put("description", "Start date in ISO format (e.g., 2024-01-01T00:00:00Z)")
        }
        putJsonObject("endDate") {
            put("type", "string")
            put("description", "End date in ISO format (e.g., 2024-12-31T23:59:59Z)")
        }
    }
}