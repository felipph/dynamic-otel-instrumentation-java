#!/bin/bash

# Sample Spring WebFlux - Startup Script
# This script starts the application with OpenTelemetry instrumentation

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
EXTENSION_DIR="$(dirname "$(dirname "$PROJECT_DIR")")"

# Default values
OTEL_AGENT_VERSION="2.25.0"
OTEL_AGENT_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"
OTEL_AGENT_PATH="${PROJECT_DIR}/target/opentelemetry-javaagent.jar"
EXTENSION_PATH="${EXTENSION_DIR}/target/dynamic-instrumentation-agent-1.1.0.jar"
CONFIG_PATH="${PROJECT_DIR}/src/main/resources/instrumentation.json"
OTEL_ENDPOINT="${OTEL_ENDPOINT:-http://localhost:4317}"
SERVICE_NAME="${SERVICE_NAME:-sample-spring-webflux}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

check_maven() {
    if ! command -v mvn &> /dev/null; then
        log_error "Maven is not installed."
        exit 1
    fi
}

build_project() {
    log_info "Building project..."
    cd "$PROJECT_DIR"
    mvn clean package -DskipTests -q
}

download_otel_agent() {
    if [ ! -f "$OTEL_AGENT_PATH" ]; then
        log_info "Downloading OpenTelemetry Java Agent..."
        curl -L -o "$OTEL_AGENT_PATH" "$OTEL_AGENT_URL"
    fi
}

build_extension() {
    if [ ! -f "$EXTENSION_PATH" ]; then
        log_info "Building extension..."
        cd "$EXTENSION_DIR"
        mvn clean package -DskipTests -q
    fi
}

start_app() {
    log_info "Starting application..."
    log_info "  Service: $SERVICE_NAME"
    log_info "  Endpoint: $OTEL_ENDPOINT"
    log_info "  Config: $CONFIG_PATH"
    log_info "  Extension: $EXTENSION_PATH"
    log_info "  Agent: $OTEL_AGENT_PATH"
    cd "$PROJECT_DIR"
    java \
        -javaagent:"$OTEL_AGENT_PATH" \
        -javaagent:"$EXTENSION_PATH" \
        -Dotel.javaagent.extensions="$EXTENSION_PATH" \
        -Dinstrumentation.config.path="$CONFIG_PATH" \
        -Dotel.service.name="$SERVICE_NAME" \
        -Dotel.exporter.otlp.endpoint="$OTEL_ENDPOINT" \
        -Dotel.exporter.otlp.protocol=grpc \
        -jar target/sample-spring-webflux-1.0.0.jar
}

main() {
    log_info "Sample Spring WebFlux - Startup"
    check_maven
    build_extension
    build_project
    download_otel_agent
    start_app
}

main "$@"
