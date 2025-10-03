package de.jug_da.standapp.mcp

import de.jug_da.data.git.*
import de.jug_da.standapp.mcp.tools.GetCommitsByAuthorInput
import de.jug_da.standapp.mcp.tools.GetAllCommitsInput
import de.jug_da.standapp.mcp.tools.SchemaUtils
import io.ktor.utils.io.streams.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.io.asSink
import kotlinx.io.buffered
import kotlinx.serialization.json.*

// Main function to run the MCP server
fun `run mcp server`() {
    // Create the MCP Server instance with git functionality
    val server = Server(
        Implementation(
            name = "standapp-git", // Tool name is "standapp-git"
            version = "1.0.0" // Version of the implementation
        ),
        ServerOptions(
            capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))
        )
    )

    // Register a tool to get commits by author and time period
    server.addTool(
        name = "get_commits_by_author",
        description = """
            Get Git commits by a specific author within a time period. Returns commit information including ID, author, date, and message.
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = SchemaUtils.getCommitsByAuthorInputSchema(),
            required = listOf("repoDir", "author", "startDate", "endDate")
        )
    ) { request ->
        val repoDir = request.arguments["repoDir"]?.jsonPrimitive?.content
        val author = request.arguments["author"]?.jsonPrimitive?.content
        val startDateStr = request.arguments["startDate"]?.jsonPrimitive?.content
        val endDateStr = request.arguments["endDate"]?.jsonPrimitive?.content
        
        if (repoDir == null || author == null || startDateStr == null || endDateStr == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("All parameters (repoDir, author, startDate, endDate) are required."))
            )
        }

        try {
            val startDate = Instant.parse(startDateStr)
            val endDate = Instant.parse(endDateStr)
            val commits = commitsByAuthorAndPeriod(repoDir, author, startDate, endDate)
            
            val commitList = commits.joinToString("\n") { commit ->
                "ID: ${commit.id}\nAuthor: ${commit.authorName} <${commit.authorEmail}>\nDate: ${commit.whenDate}\nMessage: ${commit.message}\n---"
            }
            
            CallToolResult(content = listOf(TextContent(
                if (commits.isEmpty()) "No commits found for author '$author' in the specified period."
                else "Found ${commits.size} commits:\n\n$commitList"
            )))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error processing request: ${e.message}")))
        }
    }

    // Register a tool to get all commits in a time period
    server.addTool(
        name = "get_all_commits",
        description = """
            Get all Git commits within a time period from any author. Returns commit information including ID, author, date, and message.
        """.trimIndent(),
        inputSchema = Tool.Input(
            properties = SchemaUtils.getAllCommitsInputSchema(),
            required = listOf("repoDir", "startDate", "endDate")
        )
    ) { request ->
        val repoDir = request.arguments["repoDir"]?.jsonPrimitive?.content
        val startDateStr = request.arguments["startDate"]?.jsonPrimitive?.content
        val endDateStr = request.arguments["endDate"]?.jsonPrimitive?.content
        
        if (repoDir == null || startDateStr == null || endDateStr == null) {
            return@addTool CallToolResult(
                content = listOf(TextContent("All parameters (repoDir, startDate, endDate) are required."))
            )
        }

        try {
            val startDate = Instant.parse(startDateStr)
            val endDate = Instant.parse(endDateStr)
            val commits = getAllCommitsInPeriod(repoDir, startDate, endDate)
            
            val commitList = commits.joinToString("\n") { commit ->
                "ID: ${commit.id}\nAuthor: ${commit.authorName} <${commit.authorEmail}>\nDate: ${commit.whenDate}\nMessage: ${commit.message}\n---"
            }
            
            CallToolResult(content = listOf(TextContent(
                if (commits.isEmpty()) "No commits found in the specified period."
                else "Found ${commits.size} commits:\n\n$commitList"
            )))
        } catch (e: Exception) {
            CallToolResult(content = listOf(TextContent("Error processing request: ${e.message}")))
        }
    }

    // Create a transport using standard IO for server communication
    val transport = StdioServerTransport(
        System.`in`.asInput(),
        System.out.asSink().buffered()
    )

    runBlocking {
        val session = server.connect(transport)
        val done = Job()
        transport.onClose {
            done.complete()
        }
        done.join()
    }
}