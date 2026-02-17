#!/bin/bash

# Master test script - Runs tests for all sample projects
# Usage: ./test-all.sh [project]
#   project: webmvc | webflux | batch | all (default)

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_test() { echo -e "${BLUE}[TEST]${NC} $1"; }

PROJECT="${1:-all}"

check_jaeger() {
    log_test "Checking Jaeger availability..."
    if curl -s "${JAEGER_URL:-http://localhost:16686}/api/services" > /dev/null 2>&1; then
        log_info "Jaeger is running"
        return 0
    else
        echo -e "${YELLOW}[WARN] Jaeger not detected. Start it with:${NC}"
        echo "  docker run -d --name jaeger -p 16686:16686 -p 4317:4317 jaegertracing/all-in-one:latest"
        return 1
    fi
}

test_webmvc() {
    echo ""
    echo "=========================================="
    echo "  Testing sample-spring-webmvc (port 8080)"
    echo "=========================================="
    if [ -f "$SCRIPT_DIR/sample-spring-webmvc/scripts/test.sh" ]; then
        "$SCRIPT_DIR/sample-spring-webmvc/scripts/test.sh"
    else
        echo -e "${RED}[ERROR] Test script not found${NC}"
    fi
}

test_webflux() {
    echo ""
    echo "=========================================="
    echo "  Testing sample-spring-webflux (port 8081)"
    echo "=========================================="
    if [ -f "$SCRIPT_DIR/sample-spring-webflux/scripts/test.sh" ]; then
        "$SCRIPT_DIR/sample-spring-webflux/scripts/test.sh"
    else
        echo -e "${RED}[ERROR] Test script not found${NC}"
    fi
}

test_batch() {
    echo ""
    echo "=========================================="
    echo "  Testing sample-spring-batch (port 8082)"
    echo "=========================================="
    if [ -f "$SCRIPT_DIR/sample-spring-batch/scripts/test.sh" ]; then
        "$SCRIPT_DIR/sample-spring-batch/scripts/test.sh"
    else
        echo -e "${RED}[ERROR] Test script not found${NC}"
    fi
}

show_usage() {
    echo "Usage: $0 [project]"
    echo ""
    echo "Projects:"
    echo "  webmvc   - Test Spring WebMVC sample (port 8080)"
    echo "  webflux  - Test Spring WebFlux sample (port 8081)"
    echo "  batch    - Test Spring Batch sample (port 8082)"
    echo "  all      - Test all projects (default)"
    echo ""
    echo "Prerequisites:"
    echo "  1. Build the extension: mvn clean package -DskipTests"
    echo "  2. Start Jaeger: docker run -d -p 16686:16686 -p 4317:4317 jaegertracing/all-in-one:latest"
    echo "  3. Start the sample application(s): ./scripts/start.sh"
    echo ""
    echo "Examples:"
    echo "  $0 webmvc    # Test only WebMVC"
    echo "  $0 all       # Test all projects"
}

# Main
case "$PROJECT" in
    webmvc)
        check_jaeger || true
        test_webmvc
        ;;
    webflux)
        check_jaeger || true
        test_webflux
        ;;
    batch)
        check_jaeger || true
        test_batch
        ;;
    all)
        check_jaeger || true
        test_webmvc
        test_webflux
        test_batch
        ;;
    -h|--help|help)
        show_usage
        exit 0
        ;;
    *)
        echo -e "${RED}Unknown project: $PROJECT${NC}"
        show_usage
        exit 1
        ;;
esac

echo ""
echo "=========================================="
echo "  All tests completed!"
echo "=========================================="
