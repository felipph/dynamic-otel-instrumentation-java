#!/bin/bash

# Run the microservices sample locally
# Usage: ./run.sh [port]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_DIR="$(dirname "$(dirname "$PROJECT_DIR")")"
PORT=${1:-8080}

echo "=========================================="
echo "Sample Microservices - Local Run"
echo "=========================================="
echo "Port: $PORT"
echo "Service Name: order-service-local"
echo ""

# Check if JAR exists
if [ ! -f "$ROOT_DIR/target/dynamic-instrumentation-agent-1.1.0.jar" ]; then
    echo "Building extension..."
    cd "$ROOT_DIR"
    mvn clean package -DskipTests -q
fi

# Download OTel agent if not present
OTEL_AGENT="$ROOT_DIR/docker/opentelemetry-javaagent.jar"
if [ ! -f "$OTEL_AGENT" ]; then
    echo "Downloading OpenTelemetry Java Agent..."
    curl -L -o "$OTEL_AGENT" \
        https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar
fi

echo ""
echo "Starting application..."
echo "- Jaeger UI: http://localhost:16686"
echo "- RabbitMQ Management: http://localhost:15672 (guest/guest)"
echo "- Application Health: http://localhost:$PORT/api/health"
echo ""

cd "$PROJECT_DIR"

# Build if needed
if [ ! -f "target/sample-microservices-1.0.0.jar" ]; then
    echo "Building application..."
    mvn clean package -DskipTests -q
fi

# Run with OTel agent and extension
java \
    -javaagent:"$OTEL_AGENT" \
    -javaagent:"$ROOT_DIR/target/dynamic-instrumentation-agent-1.1.0.jar" \
    -Dotel.javaagent.extensions="$ROOT_DIR/target/dynamic-instrumentation-agent-1.1.0.jar" \
    -Dotel.service.name=order-service-local \
    -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
    -Dotel.exporter.otlp.protocol=grpc \
    -Dinstrumentation.config.path="$PROJECT_DIR/config/instrumentation.json" \
    -DPORT=$PORT \
    -Dspring.application.name=order-service-local \
    -jar target/sample-microservices-1.0.0.jar
