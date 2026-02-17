#!/bin/bash

# Run the microservices stack with Docker Compose
# This starts: 2 microservice instances, RabbitMQ, Jaeger, OTel Collector

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ROOT_DIR="$(dirname "$(dirname "$PROJECT_DIR")")"

echo "=========================================="
echo "Sample Microservices - Docker Compose"
echo "=========================================="
echo ""

# Build extension if needed
if [ ! -f "$ROOT_DIR/target/dynamic-instrumentation-agent-1.0.0.jar" ]; then
    echo "Building extension..."
    cd "$ROOT_DIR"
    mvn clean package -DskipTests -q
fi

cd "$PROJECT_DIR"

echo "Starting Docker Compose stack..."
echo ""
echo "Services:"
echo "  - microservice-1 (port 8080) - Receives orders via HTTP"
echo "  - microservice-2 (port 8081) - Processes orders from queue"
echo "  - RabbitMQ (ports 5672, 15672)"
echo "  - Jaeger (port 16686)"
echo "  - OTel Collector (ports 4317, 4318)"
echo ""

docker-compose up --build

# To run in background: docker-compose up --build -d
# To view logs: docker-compose logs -f microservice-1
# To stop: docker-compose down
