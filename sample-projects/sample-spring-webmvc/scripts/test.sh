#!/bin/bash

# Test script for sample-spring-webmvc
# Verifies instrumentation by making API calls and checking traces

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

# Test Customer API
test_customers() {
    log_test "=== Testing Customer API ==="

    # Create customer
    log_test "Creating customer..."
    CUSTOMER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/customers" \
        -H "Content-Type: application/json" \
        -d '{
            "firstName": "John",
            "lastName": "Doe",
            "email": "john.doe.test@example.com",
            "phone": "+1-555-0100",
            "address": {
                "street": "123 Main St",
                "city": "New York",
                "state": "NY",
                "zipCode": "10001",
                "country": "USA"
            }
        }')

    CUSTOMER_ID=$(echo "$CUSTOMER_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

    if [ -n "$CUSTOMER_ID" ]; then
        log_pass "Customer created with ID: $CUSTOMER_ID"
    else
        log_fail "Failed to create customer: $CUSTOMER_RESPONSE"
        return 1
    fi

    # Get customer
    log_test "Fetching customer by ID..."
    curl -s "$BASE_URL/api/customers/$CUSTOMER_ID" | grep -q "john.doe.test@example.com" && \
        log_pass "Customer fetched successfully" || log_fail "Failed to fetch customer"

    # Get all customers
    log_test "Fetching all customers..."
    curl -s "$BASE_URL/api/customers" | grep -q "john.doe.test" && \
        log_pass "Customers list fetched" || log_fail "Failed to fetch customers"

    echo "$CUSTOMER_ID"
}

# Test Product API
test_products() {
    log_test "=== Testing Product API ==="

    # Create product
    log_test "Creating product..."
    PRODUCT_RESPONSE=$(curl -s -X POST "$BASE_URL/api/products" \
        -H "Content-Type: application/json" \
        -d '{
            "sku": "TEST-SKU-001",
            "name": "Test Product",
            "description": "A test product for instrumentation",
            "price": 29.99,
            "stockQuantity": 100
        }')

    PRODUCT_ID=$(echo "$PRODUCT_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

    if [ -n "$PRODUCT_ID" ]; then
        log_pass "Product created with ID: $PRODUCT_ID"
    else
        log_fail "Failed to create product: $PRODUCT_RESPONSE"
        return 1
    fi

    # Get product
    log_test "Fetching product by ID..."
    curl -s "$BASE_URL/api/products/$PRODUCT_ID" | grep -q "Test Product" && \
        log_pass "Product fetched successfully" || log_fail "Failed to fetch product"

    # Get all products
    log_test "Fetching all products..."
    curl -s "$BASE_URL/api/products" | grep -q "TEST-SKU" && \
        log_pass "Products list fetched" || log_fail "Failed to fetch products"

    echo "$PRODUCT_ID"
}

# Test Order API
test_orders() {
    log_test "=== Testing Order API ==="
    local CUSTOMER_ID=$1

    # Create order
    log_test "Creating order..."
    ORDER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/orders" \
        -H "Content-Type: application/json" \
        -d '{
            "customerId": '$CUSTOMER_ID',
            "items": [
                {
                    "productName": "Test Product",
                    "sku": "TEST-SKU-001",
                    "quantity": 2,
                    "unitPrice": 29.99
                }
            ]
        }')

    ORDER_ID=$(echo "$ORDER_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

    if [ -n "$ORDER_ID" ]; then
        log_pass "Order created with ID: $ORDER_ID"
    else
        log_fail "Failed to create order: $ORDER_RESPONSE"
        return 1
    fi

    # Get order
    log_test "Fetching order by ID..."
    curl -s "$BASE_URL/api/orders/$ORDER_ID" | grep -q "PENDING" && \
        log_pass "Order fetched successfully" || log_fail "Failed to fetch order"

    echo "$ORDER_ID"
}

# Test Payment API (async)
test_payments() {
    log_test "=== Testing Payment API (Async) ==="
    local ORDER_ID=$1

    # Process payment (async)
    log_test "Triggering async payment processing..."
    curl -s -X POST "$BASE_URL/api/payments/process?orderId=$ORDER_ID&amount=65.98&paymentMethod=CREDIT_CARD" | grep -q "202" && \
        log_pass "Payment processing triggered (async)" || log_info "Payment endpoint returned (may be 202 Accepted)"

    # Calculate shipping
    log_test "Calculating shipping..."
    SHIPPING=$(curl -s "$BASE_URL/api/payments/shipping?zipCode=10001&total=100.00")
    log_pass "Shipping calculated: $SHIPPING"

    # Validate payment method
    log_test "Validating payment method..."
    curl -s "$BASE_URL/api/payments/validate?method=CREDIT_CARD" | grep -q "true" && \
        log_pass "Payment validation works" || log_fail "Payment validation failed"
}

# Check traces in Jaeger
check_traces() {
    log_test "=== Checking Traces in Jaeger ==="

    sleep 2  # Give time for traces to be exported

    log_test "Looking for traces in Jaeger..."

    # Query Jaeger API for traces
    TRACES=$(curl -s "$JAEGER_URL/api/traces?service=$SERVICE_NAME&limit=5")

    if echo "$TRACES" | grep -q '"traceID"'; then
        TRACE_COUNT=$(echo "$TRACES" | grep -o '"traceID"' | wc -l)
        log_pass "Found $TRACE_COUNT traces in Jaeger"

        # Check for specific spans
        if echo "$TRACES" | grep -q "CustomerService"; then
            log_pass "Found CustomerService spans"
        fi
        if echo "$TRACES" | grep -q "OrderService"; then
            log_pass "Found OrderService spans"
        fi
        if echo "$TRACES" | grep -q "ProductService"; then
            log_pass "Found ProductService spans"
        fi
        if echo "$TRACES" | grep -q "PaymentService"; then
            log_pass "Found PaymentService spans"
        fi

        # Check for custom attributes
        if echo "$TRACES" | grep -q "app.customer"; then
            log_pass "Found custom attributes (app.customer.*)"
        fi
        if echo "$TRACES" | grep -q "app.order"; then
            log_pass "Found custom attributes (app.order.*)"
        fi
        if echo "$TRACES" | grep -q "app.product"; then
            log_pass "Found custom attributes (app.product.*)"
        fi

        log_info "View traces at: $JAEGER_URL/search?service=$SERVICE_NAME"
    else
        log_fail "No traces found in Jaeger"
        log_info "Make sure Jaeger is running: docker run -d -p 16686:16686 -p 4317:4317 jaegertracing/all-in-one:latest"
    fi
}

# Main test flow
main() {
    echo ""
    echo "========================================"
    echo "  Testing sample-spring-webmvc"
    echo "========================================"
    echo ""

    check_app || exit 1

    CUSTOMER_ID=$(test_customers)
    PRODUCT_ID=$(test_products)
    ORDER_ID=$(test_orders "$CUSTOMER_ID")
    test_payments "$ORDER_ID"

    echo ""
    check_traces

    echo ""
    echo "========================================"
    echo "  Test Complete"
    echo "========================================"
}

main "$@"
