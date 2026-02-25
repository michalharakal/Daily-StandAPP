# Multi-stage Docker build for Daily Stand App MCP Server
# This Dockerfile creates a production-ready container with the MCP Server

# Build stage
FROM gradle:8.5-jdk21 AS builder

LABEL org.opencontainers.image.title="Daily Stand App MCP Server"
LABEL org.opencontainers.image.description="Model Context Protocol server for Git analysis and AI-powered standup summaries"
LABEL org.opencontainers.image.vendor="JUG Darmstadt"
LABEL org.opencontainers.image.source="https://github.com/your-org/daily-stand-app"

# Set working directory
WORKDIR /app

# Copy Gradle files first for better layer caching
COPY gradle/ gradle/
COPY gradlew gradlew.bat gradle.properties settings.gradle.kts build.gradle.kts ./

# Copy version catalogs
COPY gradle/libs.versions.toml gradle/

# Download dependencies (this layer will be cached if dependencies don't change)
RUN ./gradlew dependencies --no-daemon

# Copy source code
COPY . .

# Build the application
RUN ./gradlew :mcp-server:jvmJar --no-daemon --parallel

# Verify the fat JAR was created
RUN ls -la mcp-server/build/libs/

# Runtime stage
FROM eclipse-temurin:21-jre-jammy AS runtime

# Install necessary packages
RUN apt-get update && apt-get install -y \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create application user and group for security
RUN groupadd -r mcpserver && useradd -r -g mcpserver mcpserver

# Create directories
RUN mkdir -p /app /app/config /app/logs /app/data /app/models \
    && chown -R mcpserver:mcpserver /app

# Set working directory
WORKDIR /app

# Copy the fat JAR from builder stage
COPY --from=builder /app/mcp-server/build/libs/mcp-server-fat.jar /app/mcp-server.jar

# Create default configuration directory and sample config
COPY --chown=mcpserver:mcpserver docker/mcp-server.json /app/config/
COPY --chown=mcpserver:mcpserver docker/docker-entrypoint.sh /app/

# Make entrypoint script executable
RUN chmod +x /app/docker-entrypoint.sh

# Switch to non-root user
USER mcpserver

# Expose ports
EXPOSE 8080
EXPOSE 9090

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8080/health || exit 1

# Environment variables with defaults
ENV MCP_SERVER_HOST=0.0.0.0
ENV MCP_SERVER_PORT=8080
ENV MCP_LOG_LEVEL=INFO
ENV MCP_LOG_CONSOLE_ENABLED=true
ENV MCP_LOG_FILE_ENABLED=true
ENV MCP_LOG_FILE_PATH=/app/logs/mcp-server.log
ENV MCP_PERFORMANCE_METRICS_ENABLED=true
ENV MCP_PERFORMANCE_METRICS_PORT=9090
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -XX:+UseStringDeduplication --enable-preview --add-modules jdk.incubator.vector -Xmx6g"

# Volumes for persistent data
VOLUME ["/app/config", "/app/logs", "/app/data", "/app/models"]

# Use entrypoint script
ENTRYPOINT ["/app/docker-entrypoint.sh"]

# Default command
CMD ["--config", "/app/config/mcp-server.json"]