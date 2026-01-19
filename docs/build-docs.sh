#!/bin/bash
# Build documentation with Mermaid diagrams rendered locally (no external services)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "Building documentation Docker image..."
docker build -t dailystandapp-docs .

echo "Building documentation..."
docker run --rm \
    -v "$(dirname "$SCRIPT_DIR"):/project" \
    -w /project/docs \
    dailystandapp-docs \
    antora-playbook.yml

echo "Documentation built successfully!"
echo "Output: $SCRIPT_DIR/build/site/"
echo ""
echo "To view locally: open $SCRIPT_DIR/build/site/index.html"
