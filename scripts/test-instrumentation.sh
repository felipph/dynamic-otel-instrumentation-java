#!/bin/bash

# =============================================================================
# Test script for Dynamic OTel Instrumentation
# Sends requests to the sample app and verifies spans + custom attributes in Jaeger
# =============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

APP_URL="http://localhost:8080/sample/api"
JAEGER_URL="http://localhost:16686"
SERVICE_NAME="sample-spring-mvc-app"

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN} Dynamic Instrumentation Test Script${NC}"
echo -e "${CYAN}========================================${NC}"
echo ""

# --- Step 1: Check services are up ---
echo -e "${YELLOW}[1/4] Checking services...${NC}"

if ! curl -sf "$APP_URL/products" > /dev/null 2>&1; then
    echo -e "${RED}  ERROR: Sample app is not reachable at $APP_URL${NC}"
    exit 1
fi
echo -e "${GREEN}  Sample app is up${NC}"

if ! curl -sf "$JAEGER_URL/" > /dev/null 2>&1; then
    echo -e "${RED}  ERROR: Jaeger is not reachable at $JAEGER_URL${NC}"
    exit 1
fi
echo -e "${GREEN}  Jaeger is up${NC}"
echo ""

# --- Step 2: Send test requests ---
echo -e "${YELLOW}[2/4] Sending test requests...${NC}"

echo -n "  GET /products/1 ... "
curl -sf "$APP_URL/products/1" > /dev/null && echo -e "${GREEN}OK${NC}" || echo -e "${RED}FAIL${NC}"

echo -n "  GET /orders/1 ... "
curl -sf "$APP_URL/orders/1" > /dev/null && echo -e "${GREEN}OK${NC}" || echo -e "${RED}FAIL${NC}"

echo -n "  GET /customers/1 ... "
curl -sf "$APP_URL/customers/1" > /dev/null && echo -e "${GREEN}OK${NC}" || echo -e "${RED}FAIL${NC}"

echo ""

# --- Step 3: Wait for traces to be exported ---
echo -e "${YELLOW}[3/4] Waiting 5s for trace export...${NC}"
sleep 5
echo ""

# --- Step 4: Query Jaeger and verify custom attributes ---
echo -e "${YELLOW}[4/4] Verifying traces in Jaeger...${NC}"
echo ""

PASS=0
FAIL=0

check_trace() {
    local operation="$1"
    local expected_attr="$2"
    local description="$3"

    local result
    result=$(curl -s "${JAEGER_URL}/api/traces?service=${SERVICE_NAME}&limit=1&lookback=2m&operation=${operation}" 2>&1)

    local span_count
    span_count=$(echo "$result" | python3 -c "
import sys,json
d=json.load(sys.stdin)
traces=d.get('data',[])
print(sum(len(t['spans']) for t in traces))
" 2>/dev/null || echo "0")

    if [ "$span_count" = "0" ]; then
        echo -e "  ${RED}FAIL${NC} $description"
        echo -e "       No traces found for operation: $operation"
        FAIL=$((FAIL + 1))
        return
    fi

    if [ -n "$expected_attr" ]; then
        local attr_found
        attr_found=$(echo "$result" | python3 -c "
import sys,json
d=json.load(sys.stdin)
traces=d.get('data',[])
for t in traces:
    for span in t['spans']:
        for tag in span.get('tags',[]):
            if tag['key'] == '${expected_attr}':
                print(tag['value'])
                sys.exit(0)
print('')
" 2>/dev/null)

        if [ -n "$attr_found" ]; then
            echo -e "  ${GREEN}PASS${NC} $description"
            echo -e "       ${expected_attr} = ${attr_found}"
            PASS=$((PASS + 1))
        else
            echo -e "  ${RED}FAIL${NC} $description"
            echo -e "       Attribute '${expected_attr}' not found on span"
            FAIL=$((FAIL + 1))
        fi
    else
        echo -e "  ${GREEN}PASS${NC} $description (span exists)"
        PASS=$((PASS + 1))
    fi
}

check_trace "ProductController.getProduct" "app.product_id" \
    "ProductController.getProduct has app.product_id"

check_trace "InventoryService.getProduct" "app.service.product_id" \
    "InventoryService.getProduct has app.service.product_id"

check_trace "OrderController.getOrder" "app.order_id" \
    "OrderController.getOrder has app.order_id"

check_trace "OrderService.getOrderById" "app.service.order_id" \
    "OrderService.getOrderById has app.service.order_id"

check_trace "OrderRepository.findById" "app.repository.order_id" \
    "OrderRepository.findById has app.repository.order_id"

check_trace "CustomerService.getCustomer" "app.service.customer_id" \
    "CustomerService.getCustomer has app.service.customer_id"

check_trace "CustomerRepository.findById" "app.repository.customer_id" \
    "CustomerRepository.findById has app.repository.customer_id"

# Check interface detection attribute
echo ""
echo -e "${YELLOW}  Interface detection:${NC}"

check_trace "InventoryService.getProduct" "code.instrumented.interface" \
    "InventoryService.getProduct has code.instrumented.interface"

check_trace "OrderService.getOrderById" "code.instrumented.interface" \
    "OrderService.getOrderById has code.instrumented.interface"

check_trace "CustomerService.getCustomer" "code.instrumented.interface" \
    "CustomerService.getCustomer has code.instrumented.interface"

# --- Summary ---
echo ""
echo -e "${CYAN}========================================${NC}"
TOTAL=$((PASS + FAIL))
if [ "$FAIL" -eq 0 ]; then
    echo -e "${GREEN}  ALL $TOTAL CHECKS PASSED${NC}"
else
    echo -e "${RED}  $FAIL/$TOTAL CHECKS FAILED${NC}"
fi
echo -e "${CYAN}========================================${NC}"

exit $FAIL
