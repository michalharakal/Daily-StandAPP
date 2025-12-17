# dAily-StandAPP

[![JavaLand_2025-Banner](https://www.javaland.eu/fileadmin/Event/JavaLand/Banner/2025/JavaLand_2025-Banner-512x256px-I_m_a_Speaker.jpg)](https://meine.doag.org/events/javaland/2025/agenda/#agendaId.5382)


### Step 1: Build the MCP Server

```bash
# Build the MCP server module
./gradlew :mcp-server:jvmJar

# Verify the JAR was created
ls -la mcp-server/build/libs/mcp-server-jvm.jar
```

Expected output: You should see the `mcp-server-fat.jar` file with recent timestamp.

### Step 2: Start the MCP Server

```bash
# Start the server on localhost:8080
java -jar mcp-server/build/libs/mcp-server-jvm.jar --host localhost --port 8080
```

```bash
$ java -jar mcp-server/build/libs/mcp-server-jvm.jar --host localhost --port 8080
[main] INFO io.modelcontextprotocol.kotlin.sdk.server.Server - Registering tool: get_commits_by_author
[main] INFO io.modelcontextprotocol.kotlin.sdk.server.Server - Registering tool: get_all_commits
```

