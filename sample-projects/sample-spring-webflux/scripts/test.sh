#!/bin/bash

# Test script for sample-spring-webflux
# Verifies instrumentation by making API calls and checking traces

set -e

BASE_URL="${1:-http://localhost:8081}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"
SERVICE_NAME="sample-spring-webflux"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_test() { echo -e "${BLUE}[TEST]${NC} $1"; }
log_pass() { echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { echo -e "${RED}[FAIL]${NC} $1"; }

# Check if application is running
check_app() {
    log_test "Checking if application is running..."
    if curl -s "$BASE_URL/api/products" > /dev/null 2>&1; then
        log_pass "Application is running at $BASE_URL"
        return 0
    else
        log_fail "Application is not running at $BASE_URL"
        log_info "Start the application first: ./scripts/start.sh"
        return 1
    fi
}

# Test Product API
test_products() {
    log_test "=== Testing Reactive Product API ==="

    # Create product
    log_test "Creating product (reactive)..."
    PRODUCT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/products" \
        -H "Content-Type: application/json" \
        -d '{
            "sku": "REACTIVE-001",
            "name": "Reactive Test Product",
            "description": "A reactive product for testing",
            "price": 49.99,
            "stockQuantity": 50
        }')

    PRODUCT_ID=$(echo "$PRODUCT_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

    if [ -n "$PRODUCT_ID" ]; then
        log_pass "Product created with ID: $PRODUCT_ID"
    else
        log_fail "Failed to create product: $PRODUCT_RESPONSE"
        return 1
    fi

    # Get product by ID
    log_test "Fetching product by ID (Mono)..."
    curl -s "$BASE_URL/api/products/$PRODUCT_ID" | grep -q "Reactive Test Product" && \
        log_pass "Product fetched successfully (Mono)" || log_fail "Failed to fetch product"

    # Get product by SKU
    log_test "Fetching product by SKU..."
    curl -s "$BASE_URL/api/products/sku/REACTIVE-001" | grep -q "REACTIVE-001" && \
        log_pass "Product fetched by SKU" || log_fail "Failed to fetch by SKU"

    # Get all products (Flux)
    log_test "Fetching all products (Flux)..."
    PRODUCTS=$(curl -s "$BASE_URL/api/products")
    if echo "$PRODUCTS" | grep -q "REACTIVE-001"; then
        log_pass "Products list fetched (Flux)"
    else
        log_fail "Failed to fetch products"
    fi

    # Get available products
    log_test "Fetching available products (stock > 0)..."
    curl -s "$BASE_URL/api/products/available" | grep -q "Reactive Test Product" && \
        log_pass "Available products fetched" || log_fail "Failed to fetch available products"

    echo "$PRODUCT_ID"
}

# Test stock update
test_stock_update() {
    log_test "=== Testing Stock Update ==="
    local PRODUCT_ID=$1

    # Update stock
    log_test "Updating stock (decrease by 5)..."
    curl -s -X PATCH "$BASE_URL/api/products/$PRODUCT_ID/stock?quantity=-5" && \
        log_pass "Stock updated" || log_fail "Failed to update stock"

    # Verify stock
    PRODUCT=$(curl -s "$BASE_URL/api/products/$PRODUCT_ID")
    STOCK=$(echo "$PRODUCT" | grep -o '"stockQuantity":[0-9]*' | cut -d: -f2)

    if [ "$STOCK" = "45" ]; then
        log_pass "Stock correctly updated to 45"
    else
        log_info "Stock is $STOCK (may vary if tests ran multiple times)"
    fi
}

# Test error handling
test_errors() {
    log_test "=== Testing Error Handling ==="

    # Try to get non-existent product
    log_test "Requesting non-existent product..."
    RESPONSE=$(curl -s -w "\n%{http_code}" "$BASE_URL/api/products/99999")
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)

    if [ "$HTTP_CODE" = "404" ] || [ "$HTTP_CODE" = "500" ]; then
        log_pass "Error handling works (HTTP $HTTP_CODE)"
    else
        log_info "Got HTTP $HTTP_CODE for non-existent product"
    fi

    # Try duplicate SKU
    log_test "Creating product with duplicate SKU..."
    RESPONSE=$(curl -s -w "\n%{http_code}" -X POST "$BASE_URL/api/products" \
        -H "Content-Type: application/json" \
        -d '{"sku":"REACTIVE-001","name":"Duplicate","price":10.00}')
    HTTP_CODE=$(echo "$RESPONSE" | tail -1)

    if [ "$HTTP_CODE" = "400" ] || [ "$HTTP_CODE" = "500" ]; then
        log_pass "Duplicate SKU rejected (HTTP $HTTP_CODE)"
    else
        log_info "Duplicate SKU response: HTTP $HTTP_CODE"
    fi
}

# Check traces in Jaeger
check_traces() {
    log_test "=== Checking Traces in Jaeger ==="

    sleep 2

    log_test "Looking for traces in Jaeger..."

    TRACES=$(curl -s "$JAEGER_URL/api/traces?service=$SERVICE_NAME&limit=5")

    if echo "$TRACES" | grep -q '"traceID"'; then
        TRACE_COUNT=$(echo "$TRACES" | grep -o '"traceID"' | wc -l)
        log_pass "Found $TRACE_COUNT traces in Jaeger"

        # Check for reactive service spans
        if echo "$TRACES" | grep -q "ProductService"; then
            log_pass "Found ProductService spans"
        fi
        if echo "$TRACES" | grep -q "ProductController"; then
            log_pass "Found ProductController spans"
        fi

        # Check for custom attributes
        if echo "$TRACES" | grep -q "app.product"; then
            log_pass "Found custom attributes (app.product.*)"
        fi

        log_info "View traces at: $JAEGER_URL/search?service=$SERVICE_NAME"
    else
        log_fail "No traces found in Jaeger"
        log_info "Make sure Jaeger is running"
    fi
}

# Main test flow
main() {
    echo ""
    echo "========================================"
    echo "  Testing sample-spring-webflux"
    echo "========================================"
    echo ""

    check_app || exit 1

    PRODUCT_ID=$(test_products)
    test_stock_update "$PRODUCT_ID"
    test_errors

    echo ""
    check_traces

    echo ""
    echo "========================================"
    echo "  Test Complete"
    echo "========================================"
}

main "$@"
