#!/bin/bash

# Test script for PaymentService instrumentation
# Verifies that custom attributes are captured correctly in traces

set -e

BASE_URL="${1:-http://localhost:8080}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"
SERVICE_NAME="sample-spring-webmvc"

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
    if curl -s "$BASE_URL/actuator/health" > /dev/null 2>&1 || curl -s "$BASE_URL/api/customers" > /dev/null 2>&1; then
        log_pass "Application is running at $BASE_URL"
        return 0
    else
        log_fail "Application is not running at $BASE_URL"
        log_info "Start the application first: ./scripts/start.sh"
        return 1
    fi
}

# Test processPaymentAsync instrumentation
test_process_payment_async() {
    log_test "=== Testing processPaymentAsync instrumentation ==="

    local ORDER_ID=12345
    local AMOUNT=99.99
    local PAYMENT_METHOD="CREDIT_CARD"

    log_test "Calling POST /api/payments/process with orderId=$ORDER_ID, amount=$AMOUNT, method=$PAYMENT_METHOD"

    HTTP_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
        "$BASE_URL/api/payments/process?orderId=$ORDER_ID&amount=$AMOUNT&paymentMethod=$PAYMENT_METHOD")

    if [ "$HTTP_STATUS" = "202" ]; then
        log_pass "Payment processing triggered (HTTP 202 Accepted)"
    else
        log_fail "Expected HTTP 202, got: $HTTP_STATUS"
        return 1
    fi

    # Store values for trace verification
    echo "$ORDER_ID:$AMOUNT:$PAYMENT_METHOD"
}

# Test calculateShipping instrumentation
test_calculate_shipping() {
    log_test "=== Testing calculateShipping instrumentation ==="

    local ZIPCODE="10001"
    local TOTAL="150.00"

    log_test "Calling GET /api/payments/shipping with zipCode=$ZIPCODE, total=$TOTAL"

    SHIPPING_RESPONSE=$(curl -s "$BASE_URL/api/payments/shipping?zipCode=$ZIPCODE&total=$TOTAL")

    if [ -n "$SHIPPING_RESPONSE" ]; then
        log_pass "Shipping calculated: $SHIPPING_RESPONSE"
    else
        log_fail "No response from shipping endpoint"
        return 1
    fi

    # Store values for trace verification
    echo "$ZIPCODE:$TOTAL"
}

# Check Jaeger traces for PaymentService attributes
check_payment_traces() {
    log_test "=== Checking PaymentService traces in Jaeger ==="

    log_info "Waiting for traces to be exported..."
    sleep 3

    log_test "Querying Jaeger for traces..."

    # Query Jaeger API for traces with PaymentService
    TRACES=$(curl -s "$JAEGER_URL/api/traces?service=$SERVICE_NAME&limit=10")

    if [ -z "$TRACES" ] || ! echo "$TRACES" | grep -q '"traceID"'; then
        log_fail "No traces found in Jaeger"
        log_info "Make sure Jaeger is running: docker run -d -p 16686:16686 -p 4317:4317 jaegertracing/all-in-one:latest"
        return 1
    fi

    TRACE_COUNT=$(echo "$TRACES" | grep -o '"traceID"' | wc -l)
    log_pass "Found $TRACE_COUNT traces in Jaeger"

    # Check for PaymentService spans
    log_test "Looking for PaymentService spans..."
    if echo "$TRACES" | grep -q "PaymentService"; then
        log_pass "Found PaymentService spans"
    else
        log_fail "No PaymentService spans found"
        return 1
    fi

    # Check for processPaymentAsync specific attributes
    log_test "Verifying processPaymentAsync attributes..."
    local PAYMENT_ATTRS_FOUND=0

    if echo "$TRACES" | grep -q "app.payment.order_id"; then
        log_pass "Found attribute: app.payment.order_id"
        ((PAYMENT_ATTRS_FOUND++))
    else
        log_fail "Missing attribute: app.payment.order_id"
    fi

    if echo "$TRACES" | grep -q "app.payment.amount"; then
        log_pass "Found attribute: app.payment.amount"
        ((PAYMENT_ATTRS_FOUND++))
    else
        log_fail "Missing attribute: app.payment.amount"
    fi

    if echo "$TRACES" | grep -q "app.payment.method"; then
        log_pass "Found attribute: app.payment.method"
        ((PAYMENT_ATTRS_FOUND++))
    else
        log_fail "Missing attribute: app.payment.method"
    fi

    # Check for calculateShipping specific attributes
    log_test "Verifying calculateShipping attributes..."
    local SHIPPING_ATTRS_FOUND=0

    if echo "$TRACES" | grep -q "app.shipping.zipcode"; then
        log_pass "Found attribute: app.shipping.zipcode"
        ((SHIPPING_ATTRS_FOUND++))
    else
        log_fail "Missing attribute: app.shipping.zipcode"
    fi

    if echo "$TRACES" | grep -q "app.shipping.order_total"; then
        log_pass "Found attribute: app.shipping.order_total"
        ((SHIPPING_ATTRS_FOUND++))
    else
        log_fail "Missing attribute: app.shipping.order_total"
    fi

    # Summary
    echo ""
    log_test "=== Test Summary ==="

    TOTAL_EXPECTED=5
    TOTAL_FOUND=$((PAYMENT_ATTRS_FOUND + SHIPPING_ATTRS_FOUND))

    if [ "$TOTAL_FOUND" -eq "$TOTAL_EXPECTED" ]; then
        log_pass "All $TOTAL_EXPECTED PaymentService attributes found in traces!"
        log_info "View traces at: $JAEGER_URL/search?service=$SERVICE_NAME"
        return 0
    else
        log_fail "Only $TOTAL_FOUND of $TOTAL_EXPECTED attributes found"
        log_info "View traces at: $JAEGER_URL/search?service=$SERVICE_NAME"
        return 1
    fi
}

# Main test flow
main() {
    echo ""
    echo "========================================"
    echo "  Testing PaymentService Instrumentation"
    echo "========================================"
    echo ""
    echo "Expected attributes from instrumentation.json:"
    echo "  processPaymentAsync:"
    echo "    - app.payment.order_id (argIndex 0)"
    echo "    - app.payment.amount (argIndex 1)"
    echo "    - app.payment.method (argIndex 2)"
    echo "  calculateShipping:"
    echo "    - app.shipping.zipcode (argIndex 0)"
    echo "    - app.shipping.order_total (argIndex 1)"
    echo ""

    check_app || exit 1

    test_process_payment_async
    test_calculate_shipping

    echo ""
    check_payment_traces

    local result=$?

    echo ""
    echo "========================================"
    echo "  PaymentService Test Complete"
    echo "========================================"

    exit $result
}

main "$@"
