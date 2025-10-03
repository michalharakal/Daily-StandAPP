# Claude Desktop Integration Guide

This guide shows how to integrate the Daily Stand App MCP server with Claude Desktop.

## Prerequisites

- Claude Desktop application installed
- Daily Stand App MCP server built and ready to run
- Node.js installed (for MCP proxy if needed)

## Configuration

### Option 1: Direct WebSocket Connection (Recommended)

Configure Claude Desktop to connect directly to the MCP server by editing your Claude Desktop configuration file:

**Location:**
- macOS: `~/Library/Application Support/Claude/claude_desktop_config.json`
- Windows: `%APPDATA%/Claude/claude_desktop_config.json`
- Linux: `~/.config/claude/claude_desktop_config.json`

**Configuration:**
```json
{
  "mcpServers": {
    "daily-stand-app": {
      "command": "java",
      "args": [
        "-jar",
        "/path/to/your/daily-stand-app/mcp-server/build/libs/mcp-server-jvm.jar"
      ],
      "env": {
        "MCP_SERVER_PORT": "8080",
        "MCP_SERVER_HOST": "localhost"
      }
    }
  }
}
```

### Option 2: Using Gradle Run Command

```json
{
  "mcpServers": {
    "daily-stand-app": {
      "command": "bash",
      "args": [
        "-c",
        "cd /path/to/your/daily-stand-app && ./gradlew :mcp-server:run"
      ]
    }
  }
}
```

## Usage Examples

### 1. Basic Git Analysis

**Prompt to Claude:**
```
Use the git_analysis tool to analyze the commits in my repository at /Users/username/projects/my-project for the last 7 days.
```

**Expected Response:**
Claude will call the `git_analysis` tool with:
```json
{
  "repository_path": "/Users/username/projects/my-project",
  "days": 7,
  "output_format": "summary"
}
```

### 2. Author-Specific Analysis

**Prompt to Claude:**
```
Show me a detailed analysis of all commits by john.doe@example.com in the repository /path/to/repo for the last 14 days, including statistics.
```

**Tool Call:**
```json
{
  "repository_path": "/path/to/repo",
  "days": 14,
  "author": "john.doe@example.com",
  "output_format": "detailed",
  "include_stats": true
}
```

### 3. Generate Standup Summary

**Prompt to Claude:**
```
Generate a concise standup summary for yesterday's work from my commits in /Users/username/projects/my-project.
```

**Tool Call:**
```json
{
  "repository_path": "/Users/username/projects/my-project",
  "days": 1,
  "style": "concise",
  "team_focused": false
}
```

### 4. Team-Focused Summary

**Prompt to Claude:**
```
Create a team-focused standup summary with technical details for the last 3 days of work in our main repository at /company/projects/main-app.
```

**Tool Call:**
```json
{
  "repository_path": "/company/projects/main-app",
  "days": 3,
  "style": "detailed",
  "team_focused": true,
  "include_technical_details": true
}
```

### 5. JSON Export for Further Processing

**Prompt to Claude:**
```
Export the last 30 days of commit data from /path/to/repo in JSON format so I can analyze it further.
```

**Tool Call:**
```json
{
  "repository_path": "/path/to/repo",
  "days": 30,
  "output_format": "json",
  "include_stats": true
}
```

## Advanced Workflows

### Daily Standup Preparation

1. **Morning Routine:**
   ```
   Generate my daily standup summary for yesterday's commits in /my/work/repo. Make it concise and focus on what I accomplished.
   ```

2. **Team Meeting Prep:**
   ```
   Create a team-focused summary of the last 3 days of work in /team/project, including technical details for our sprint review.
   ```

### Code Review Preparation

1. **Review Recent Changes:**
   ```
   Show me a detailed analysis of all commits in /project/repo for the last 5 days, organized by author.
   ```

2. **Focus on Specific Developer:**
   ```
   Analyze commits by sarah.smith@company.com in /project/repo for the last week with detailed technical information.
   ```

### Sprint Reporting

1. **Sprint Summary:**
   ```
   Generate a comprehensive summary of all development work in /sprint/repo for the last 14 days, formatted for our sprint retrospective.
   ```

## Troubleshooting

### Server Not Starting
- Check that Java 21+ is installed
- Verify the path to the JAR file is correct
- Check that port 8080 is available

### Tool Not Found
- Restart Claude Desktop after configuration changes
- Verify the MCP server is running and accessible
- Check Claude Desktop logs for connection errors

### Permission Errors
- Ensure Claude Desktop has read access to your Git repositories
- Check that the specified repository paths exist and are Git repositories
- Verify file permissions on the repository directories

## Tips and Best Practices

1. **Repository Paths:** Always use absolute paths for better reliability
2. **Author Filtering:** Use the exact email address as it appears in Git commits
3. **Day Ranges:** Be mindful of performance with large day ranges (30+ days)
4. **Output Formats:** Use "json" format when you need to process the data further
5. **Team Summaries:** Enable `team_focused` for standup meetings with multiple developers