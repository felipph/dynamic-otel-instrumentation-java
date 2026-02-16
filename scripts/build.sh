#!/bin/bash

# Build script for Dynamic OpenTelemetry Instrumentation Agent
# This script builds the agent JAR with all dependencies shaded

set -e  # Exit on error
set -u  # Exit on undefined variable

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"

echo "=========================================="
echo "Dynamic Instrumentation Agent Build Script"
echo "=========================================="

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}Error: Maven is not installed${NC}"
    echo "Please install Maven 3.6+ to build this project"
    exit 1
fi

# Display Maven version
echo -e "${GREEN}Maven version:${NC}"
mvn --version | head -n 1

echo ""
echo "Building project..."

# Navigate to project root
cd "$PROJECT_ROOT"

# Clean and package with Maven
echo "Running: mvn clean package"
mvn clean package -DskipTests

# Check if build was successful
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}Build successful!${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo "Agent JAR location: target/dynamic-instrumentation-agent.jar"
    echo ""
    echo "To use the agent, add to your Java command:"
    echo "  -javaagent:/path/to/dynamic-instrumentation-agent.jar"
    echo ""
    echo "Or set the environment variable for config path:"
    echo "  export INSTRUMENTATION_CONFIG_PATH=/path/to/instrumentation.json"
    echo ""
else
    echo ""
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}Build failed!${NC}"
    echo -e "${RED}========================================${NC}"
    exit 1
fi
