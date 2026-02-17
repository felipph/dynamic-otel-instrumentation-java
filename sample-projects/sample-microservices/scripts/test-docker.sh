#!/bin/bash

# Test script for microservices running in Docker
# Tests both instances and the messaging flow between them

echo "=========================================="
echo "Sample Microservices - Docker Test"
echo "=========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo "Testing Instance 1 (Port 8080) - Order Receiver"
echo "================================================"

# Create order on instance 1
echo -n "Creating order on instance 1... "
RESPONSE=$(curl -s -X POST -H "Content-Type: application/json" \
    -d '{"customerId":"DOCKER-CUST-001","productId":"PROD-001","quantity":3,"unitPrice":100.00}' \
    http://localhost:8080/api/orders 2>/dev/null)
ORDER_ID=$(echo $RESPONSE | grep -o '"orderId":"[^"]*"' | cut -d'"' -f4)

if [ -n "$ORDER_ID" ]; then
    echo -e "${GREEN}OK${NC}"
    echo "   Order ID: $ORDER_ID"
else
    echo -e "${RED}FAILED${NC}"
fi

echo ""
echo "Testing Instance 2 (Port 8081) - Order Processor"
echo "================================================"

# Check stats on instance 2
echo -n "Checking stats on instance 2... "
curl -s http://localhost:8081/api/orders/stats 2>/dev/null | python3 -m json.tool 2>/dev/null || \
    curl -s http://localhost:8081/api/orders/stats 2>/dev/null

echo ""
echo ""
echo "Waiting for message to be processed..."
sleep 3

# Check order was processed (can be on either instance)
echo ""
echo "Checking order status..."
echo -n "Instance 1: "
curl -s http://localhost:8080/api/orders/$ORDER_ID 2>/dev/null | grep -o '"status":"[^"]*"'

echo -n "Instance 2: "
curl -s http://localhost:8081/api/orders/$ORDER_ID 2>/dev/null | grep -o '"status":"[^"]*"'

echo ""
echo "=========================================="
echo "Check traces in Jaeger: http://localhost:16686"
echo "Look for services: order-service-1, order-service-2"
echo ""
echo "Check RabbitMQ Management: http://localhost:15672"
echo "Login: guest/guest"
echo "=========================================="
