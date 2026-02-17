#!/bin/bash

# Test script for sample-spring-batch
# Verifies instrumentation by triggering batch jobs and checking traces

set -e

BASE_URL="${1:-http://localhost:8082}"
JAEGER_URL="${JAEGER_URL:-http://localhost:16686}"
SERVICE_NAME="sample-spring-batch"

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
    if curl -s "$BASE_URL/api/batch/processed" > /dev/null 2>&1; then
        log_pass "Application is running at $BASE_URL"
        return 0
    else
        log_fail "Application is not running at $BASE_URL"
        log_info "Start the application first: ./scripts/start.sh"
        return 1
    fi
}

# Clear previous runs
clear_processed() {
    log_test "Clearing previous processed transactions..."
    curl -s -X DELETE "$BASE_URL/api/batch/processed" > /dev/null
    log_pass "Cleared previous data"
}

# Run batch job
run_batch_job() {
    log_test "=== Running Batch Job ==="

    log_test "Starting transaction processing job..."
    JOB_RESPONSE=$(curl -s -X POST "$BASE_URL/api/batch/run")

    JOB_ID=$(echo "$JOB_RESPONSE" | grep -o '"jobId":[0-9]*' | cut -d: -f2)
    STATUS=$(echo "$JOB_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    PROCESSED=$(echo "$JOB_RESPONSE" | grep -o '"processedCount":[0-9]*' | cut -d: -f2)

    if [ -n "$JOB_ID" ]; then
        log_pass "Job started with ID: $JOB_ID"
        log_info "Status: $STATUS"
        log_info "Processed count: $PROCESSED"
    else
        log_fail "Failed to start job: $JOB_RESPONSE"
        return 1
    fi

    # Wait for job to complete
    log_test "Waiting for job to complete..."
    sleep 3

    echo "$JOB_ID"
}

# Check processed transactions
check_processed() {
    log_test "=== Checking Processed Transactions ==="

    log_test "Fetching processed transactions..."
    RESPONSE=$(curl -s "$BASE_URL/api/batch/processed")

    COUNT=$(echo "$RESPONSE" | grep -o '"count":[0-9]*' | cut -d: -f2)

    if [ -n "$COUNT" ] && [ "$COUNT" -gt 0 ]; then
        log_pass "Processed $COUNT transactions"

        # Check for specific transaction statuses
        if echo "$RESPONSE" | grep -q "PROCESSED"; then
            log_pass "Found PROCESSED transactions"
        fi

        # Show sample transactions
        log_info "Sample transaction data:"
        echo "$RESPONSE" | head -c 500
        echo "..."
    else
        log_fail "No transactions processed"
    fi
}

# Run multiple batch jobs
test_multiple_runs() {
    log_test "=== Testing Multiple Job Runs ==="

    # Clear and run again
    clear_processed

    log_test "Running second batch job..."
    curl -s -X POST "$BASE_URL/api/batch/run" > /dev/null
    sleep 3

    RESPONSE=$(curl -s "$BASE_URL/api/batch/processed")
    COUNT=$(echo "$RESPONSE" | grep -o '"count":[0-9]*' | cut -d: -f2)

    log_pass "Second job processed $COUNT transactions"
}

# Check traces in Jaeger
check_traces() {
    log_test "=== Checking Traces in Jaeger ==="

    sleep 2

    log_test "Looking for traces in Jaeger..."

    TRACES=$(curl -s "$JAEGER_URL/api/traces?service=$SERVICE_NAME&limit=10")

    if echo "$TRACES" | grep -q '"traceID"'; then
        TRACE_COUNT=$(echo "$TRACES" | grep -o '"traceID"' | wc -l)
        log_pass "Found $TRACE_COUNT traces in Jaeger"

        # Check for batch component spans
        if echo "$TRACES" | grep -q "TransactionReader"; then
            log_pass "Found TransactionReader spans"
        fi
        if echo "$TRACES" | grep -q "TransactionProcessor"; then
            log_pass "Found TransactionProcessor spans"
        fi
        if echo "$TRACES" | grep -q "TransactionWriter"; then
            log_pass "Found TransactionWriter spans"
        fi

        # Check for custom attributes
        if echo "$TRACES" | grep -q "app.batch"; then
            log_pass "Found custom attributes (app.batch.*)"
        fi
        if echo "$TRACES" | grep -q "app.batch.txn_id"; then
            log_pass "Found transaction ID attributes"
        fi
        if echo "$TRACES" | grep -q "app.batch.processor.result_status"; then
            log_pass "Found processor result status attributes"
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
    echo "  Testing sample-spring-batch"
    echo "========================================"
    echo ""

    check_app || exit 1

    clear_processed
    JOB_ID=$(run_batch_job)
    check_processed
    test_multiple_runs

    echo ""
    check_traces

    echo ""
    echo "========================================"
    echo "  Test Complete"
    echo "========================================"
}

main "$@"
