#!/bin/bash

# Test script for microservices sample
# Tests HTTP endpoints and messaging flow

BASE_URL=${1:-http://localhost:8080}
echo "=========================================="
echo "Sample Microservices - Integration Test"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo ""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test counter
TESTS_PASSED=0
TESTS_FAILED=0

test_endpoint() {
    local name="$1"
    local url="$2"
    local expected_status="$3"

    echo -n "Testing $name... "

    response=$(curl -s -w "\n%{http_code}" "$url" 2>/dev/null)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "$expected_status" ]; then
        echo -e "${GREEN}PASSED${NC} (HTTP $http_code)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        if [ -n "$body" ]; then
            echo "   Response: $(echo $body | head -c 100)..."
        fi
    else
        echo -e "${RED}FAILED${NC} (Expected HTTP $expected_status, got HTTP $http_code)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
    fi
}

test_post() {
    local name="$1"
    local url="$2"
    local data="$3"
    local expected_status="$4"

    echo -n "Testing $name... "

    response=$(curl -s -w "\n%{http_code}" -X POST -H "Content-Type: application/json" -d "$data" "$url" 2>/dev/null)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" == "$expected_status" ]; then
        echo -e "${GREEN}PASSED${NC} (HTTP $http_code)"
        TESTS_PASSED=$((TESTS_PASSED + 1))
        if [ -n "$body" ]; then
            echo "   Response: $body"
        fi
        # Return the order ID for subsequent tests
        echo "$body" | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4
    else
        echo -e "${RED}FAILED${NC} (Expected HTTP $expected_status, got HTTP $http_code)"
        TESTS_FAILED=$((TESTS_FAILED + 1))
        echo "   Response: $body"
    fi
}

echo "1. Health Check Tests"
echo "----------------------"
test_endpoint "Health endpoint" "$BASE_URL/api/health" "200"
test_endpoint "Info endpoint" "$BASE_URL/api/info" "200"

echo ""
echo "2. Order Creation Tests (HTTP -> Messaging Flow)"
echo "-------------------------------------------------"

# Create multiple orders to test messaging
ORDER_ID_1=$(test_post "Create order 1" "$BASE_URL/api/orders" \
    '{"customerId":"CUST-001","productId":"PROD-001","quantity":2,"unitPrice":999.99}')

ORDER_ID_2=$(test_post "Create order 2" "$BASE_URL/api/orders" \
    '{"customerId":"CUST-002","productId":"PROD-002","quantity":5,"unitPrice":29.99}')

ORDER_ID_3=$(test_post "Create order 3" "$BASE_URL/api/orders" \
    '{"customerId":"CUST-001","productId":"PROD-003","quantity":1,"unitPrice":79.99}')

echo ""
echo "3. Order Retrieval Tests"
echo "------------------------"
test_endpoint "Get all orders" "$BASE_URL/api/orders" "200"
test_endpoint "Get customer orders" "$BASE_URL/api/orders/customer/CUST-001" "200"
test_endpoint "Get processing stats" "$BASE_URL/api/orders/stats" "200"

echo ""
echo "4. Waiting for message processing..."
echo "-------------------------------------"
sleep 3

# Check if orders were processed
if [ -n "$ORDER_ID_1" ]; then
    test_endpoint "Get processed order 1" "$BASE_URL/api/orders/$ORDER_ID_1" "200"
fi

echo ""
echo "5. Error Handling Tests"
echo "------------------------"
test_post "Invalid product" "$BASE_URL/api/orders" \
    '{"customerId":"CUST-999","productId":"INVALID","quantity":1}' "400"

echo ""
echo "=========================================="
echo "Test Summary"
echo "=========================================="
echo -e "Passed: ${GREEN}$TESTS_PASSED${NC}"
echo -e "Failed: ${RED}$TESTS_FAILED${NC}"

echo ""
echo "Check Jaeger UI at http://localhost:16686 for traces"
echo "Service names to look for: order-service-*"

if [ $TESTS_FAILED -gt 0 ]; then
    exit 1
fi
