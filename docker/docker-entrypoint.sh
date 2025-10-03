#!/bin/bash

# Docker entrypoint script for Daily Stand App MCP Server
# This script handles initialization, configuration, and startup

set -euo pipefail

# Configuration
APP_JAR="/app/mcp-server.jar"
CONFIG_DIR="/app/config"
LOG_DIR="/app/logs"
DATA_DIR="/app/data"
MODELS_DIR="/app/models"

# Logging functions
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >&2
}

log_info() {
    log "INFO: $*"
}

log_warn() {
    log "WARN: $*"
}

log_error() {
    log "ERROR: $*"
}

# Initialize directories
init_directories() {
    log_info "Initializing directories..."
    
    # Ensure directories exist and are writable
    for dir in "$CONFIG_DIR" "$LOG_DIR" "$DATA_DIR" "$MODELS_DIR"; do
        if [ ! -d "$dir" ]; then
            log_info "Creating directory: $dir"
            mkdir -p "$dir"
        fi
        
        if [ ! -w "$dir" ]; then
            log_error "Directory not writable: $dir"
            exit 1
        fi
    done
}

# Generate default configuration if none exists
init_config() {
    local config_file="$CONFIG_DIR/mcp-server.json"
    
    if [ ! -f "$config_file" ]; then
        log_info "Generating default configuration..."
        
        # Use the application to generate a sample config
        java $JAVA_OPTS -jar "$APP_JAR" --generate-config "$config_file" || {
            log_error "Failed to generate configuration"
            exit 1
        }
        
        log_info "Default configuration created at: $config_file"
    else
        log_info "Using existing configuration: $config_file"
    fi
}

# Validate configuration
validate_config() {
    local config_file="$CONFIG_DIR/mcp-server.json"
    
    if [ -f "$config_file" ]; then
        log_info "Validating configuration..."
        
        java $JAVA_OPTS -jar "$APP_JAR" --config "$config_file" --validate-config || {
            log_error "Configuration validation failed"
            exit 1
        }
        
        log_info "Configuration is valid"
    fi
}

# Check system requirements
check_requirements() {
    log_info "Checking system requirements..."
    
    # Check Java version
    java -version 2>&1 | head -1 | log_info "Java: $(cat)"
    
    # Check available memory
    local mem_total=$(awk '/MemTotal/ {printf "%.1f GB", $2/1024/1024}' /proc/meminfo 2>/dev/null || echo "unknown")
    log_info "Total memory: $mem_total"
    
    # Check if Git is available (required for Git analysis)
    if command -v git >/dev/null 2>&1; then
        local git_version=$(git --version)
        log_info "Git: $git_version"
    else
        log_warn "Git not found - Git analysis tools will not work"
    fi
    
    # Check if curl is available (for health checks)
    if ! command -v curl >/dev/null 2>&1; then
        log_warn "curl not found - health checks may not work"
    fi
}

# Setup logging
setup_logging() {
    log_info "Setting up logging..."
    
    # Ensure log directory exists
    mkdir -p "$LOG_DIR"
    
    # Set permissions
    chmod 755 "$LOG_DIR"
    
    # Create log file if it doesn't exist
    local log_file="${MCP_LOG_FILE_PATH:-$LOG_DIR/mcp-server.log}"
    touch "$log_file"
    chmod 644 "$log_file"
}

# Handle signals for graceful shutdown
setup_signal_handlers() {
    log_info "Setting up signal handlers..."
    
    # Handle SIGTERM for graceful shutdown
    trap 'log_info "Received SIGTERM, shutting down gracefully..."; kill -TERM $SERVER_PID 2>/dev/null || true; wait $SERVER_PID' TERM
    
    # Handle SIGINT (Ctrl+C)
    trap 'log_info "Received SIGINT, shutting down gracefully..."; kill -INT $SERVER_PID 2>/dev/null || true; wait $SERVER_PID' INT
}

# Print environment information
print_env_info() {
    log_info "Environment Information:"
    log_info "  Container User: $(id)"
    log_info "  Working Directory: $(pwd)"
    log_info "  Java Opts: ${JAVA_OPTS:-<none>}"
    log_info "  MCP Server Host: ${MCP_SERVER_HOST:-<default>}"
    log_info "  MCP Server Port: ${MCP_SERVER_PORT:-<default>}"
    log_info "  Log Level: ${MCP_LOG_LEVEL:-<default>}"
    log_info "  Metrics Enabled: ${MCP_PERFORMANCE_METRICS_ENABLED:-<default>}"
    
    if [ -n "${MCP_LLM_MODEL_PATH:-}" ]; then
        log_info "  LLM Model Path: $MCP_LLM_MODEL_PATH"
        if [ -f "$MCP_LLM_MODEL_PATH" ]; then
            local model_size=$(du -h "$MCP_LLM_MODEL_PATH" | cut -f1)
            log_info "  LLM Model Size: $model_size"
        else
            log_warn "  LLM Model file not found: $MCP_LLM_MODEL_PATH"
        fi
    fi
}

# Start the MCP server
start_server() {
    local args=("$@")
    
    log_info "Starting Daily Stand App MCP Server..."
    log_info "Command line arguments: ${args[*]}"
    
    # Start the server in background
    java $JAVA_OPTS -jar "$APP_JAR" "${args[@]}" &
    SERVER_PID=$!
    
    log_info "MCP Server started with PID: $SERVER_PID"
    
    # Wait for the server process
    wait $SERVER_PID
    local exit_code=$?
    
    log_info "MCP Server stopped with exit code: $exit_code"
    return $exit_code
}

# Main execution
main() {
    log_info "=== Daily Stand App MCP Server Docker Container ==="
    log_info "Starting initialization..."
    
    # Check if JAR file exists
    if [ ! -f "$APP_JAR" ]; then
        log_error "Application JAR not found: $APP_JAR"
        exit 1
    fi
    
    # Initialize environment
    init_directories
    setup_logging
    print_env_info
    check_requirements
    
    # Handle special cases
    if [ "$1" = "--help" ] || [ "$1" = "--version" ] || [ "$1" = "--generate-config" ]; then
        # For help, version, or config generation, run directly without full initialization
        exec java $JAVA_OPTS -jar "$APP_JAR" "$@"
    fi
    
    # Initialize configuration
    init_config
    validate_config
    
    # Setup signal handling
    setup_signal_handlers
    
    # Start the server
    log_info "Initialization complete, starting server..."
    start_server "$@"
}

# Execute main function with all arguments
main "$@"