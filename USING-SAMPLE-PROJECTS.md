# Using Sample Projects

This guide explains how to use the sample projects to validate and demonstrate the dynamic OpenTelemetry instrumentation extension.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Quick Start](#quick-start)
3. [Project Overview](#project-overview)
4. [Running the Projects](#running-the-projects)
5. [Testing Instrumentation](#testing-instrumentation)
6. [Understanding Traces](#understanding-traces)
7. [Customizing Instrumentation](#customizing-instrumentation)
8. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

| Software | Version | Purpose |
|----------|---------|---------|
| Java JDK | 21+ | Runtime for sample applications |
| Maven | 3.6+ | Build tool |
| Docker | Latest | Running Jaeger for trace visualization |

### Verify Prerequisites

```bash
# Check Java version (must be 21+)
java -version

# Check Maven
mvn -version

# Check Docker
docker --version
```

### Ports Required

| Port | Service |
|------|---------|
| 16686 | Jaeger UI |
| 4317 | OTLP gRPC (Jaeger) |
| 4318 | OTLP HTTP (Jaeger) |
| 8080 | sample-spring-webmvc |
| 8081 | sample-spring-webflux |
| 8082 | sample-spring-batch |

---

## Quick Start

### Step 1: Build the Extension

```bash
cd /path/to/java-otel-instrumentation

# Build the extension JAR
./scripts/build.sh
# or
mvn clean package -DskipTests
```

This creates `target/dynamic-instrumentation-agent-1.1.0.jar`.

### Step 2: Start Jaeger

```bash
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  jaegertracing/all-in-one:latest
```

Verify Jaeger is running: http://localhost:16686

### Step 3: Start a Sample Project

```bash
# Choose one:
cd sample-projects/sample-spring-webmvc && ./scripts/start.sh
# or
cd sample-projects/sample-spring-webflux && ./scripts/start.sh
# or
cd sample-projects/sample-spring-batch && ./scripts/start.sh
```

### Step 4: Run Tests

```bash
# In another terminal, from the sample project directory:
./scripts/test.sh

# Or test all running projects:
cd sample-projects && ./test-all.sh
```

### Step 5: View Traces

Open Jaeger UI: http://localhost:16686

Select the service name (e.g., `sample-spring-webmvc`) and click "Find Traces".

---

## Project Overview

### sample-spring-webmvc

**Technologies:** Spring Web MVC, Spring Data JPA, H2, Async Methods, Virtual Threads

**Port:** 8080

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/customers` | Create customer |
| GET | `/api/customers/{id}` | Get customer by ID |
| GET | `/api/customers` | List all customers |
| PUT | `/api/customers/{id}` | Update customer |
| DELETE | `/api/customers/{id}` | Delete customer |
| POST | `/api/products` | Create product |
| GET | `/api/products/{id}` | Get product |
| GET | `/api/products` | List products |
| POST | `/api/orders` | Create order |
| GET | `/api/orders/{id}` | Get order |
| POST | `/api/orders/{id}/process` | Process order |
| POST | `/api/payments/process` | Process payment (async) |
| GET | `/api/payments/shipping` | Calculate shipping |

**Demonstrates:**
- REST Controller instrumentation
- Service layer with custom attributes
- Async method instrumentation
- Return value capture
- Chained method calls

---

### sample-spring-webflux

**Technologies:** Spring WebFlux, Spring Data R2DBC, H2, Project Reactor, Virtual Threads

**Port:** 8081

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/products` | Create product |
| GET | `/api/products/{id}` | Get product (Mono) |
| GET | `/api/products/sku/{sku}` | Get by SKU |
| GET | `/api/products` | List all (Flux) |
| GET | `/api/products/available` | List available |
| PUT | `/api/products/{id}` | Update product |
| PATCH | `/api/products/{id}/stock` | Update stock |
| DELETE | `/api/products/{id}` | Delete product |

**Demonstrates:**
- Reactive REST Controllers
- Mono/Flux patterns
- R2DBC reactive database
- Non-blocking instrumentation

---

### sample-spring-batch

**Technologies:** Spring Batch, Spring Web, H2

**Port:** 8082

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/batch/run` | Run batch job |
| GET | `/api/batch/processed` | Get processed transactions |
| DELETE | `/api/batch/processed` | Clear processed data |

**Demonstrates:**
- Chunk-oriented processing
- Item Reader instrumentation
- Item Processor instrumentation
- Item Writer instrumentation
- Batch attribute extraction

---

## Running the Projects

### Individual Project Startup

Each project has a startup script that:
1. Builds the project (if needed)
2. Downloads the OpenTelemetry Java Agent
3. Builds the extension (if needed)
4. Starts the application with instrumentation

```bash
# WebMVC
cd sample-projects/sample-spring-webmvc
./scripts/start.sh

# WebFlux
cd sample-projects/sample-spring-webflux
./scripts/start.sh

# Batch
cd sample-projects/sample-spring-batch
./scripts/start.sh
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_ENDPOINT` | `http://localhost:4317` | OTLP collector endpoint |
| `SERVICE_NAME` | (project-specific) | Service name in traces |

**Example:**

```bash
# Custom endpoint
OTEL_ENDPOINT=http://collector:4317 ./scripts/start.sh

# Custom service name
SERVICE_NAME=my-custom-name ./scripts/start.sh
```

### Running Multiple Projects

You can run multiple projects simultaneously on different ports:

```bash
# Terminal 1
cd sample-projects/sample-spring-webmvc && ./scripts/start.sh

# Terminal 2
cd sample-projects/sample-spring-webflux && ./scripts/start.sh

# Terminal 3
cd sample-projects/sample-spring-batch && ./scripts/start.sh
```

---

## Testing Instrumentation

### Running Test Scripts

Each project includes a test script that validates the instrumentation:

```bash
# Test individual project
cd sample-projects/sample-spring-webmvc
./scripts/test.sh

# Test with custom URL
./scripts/test.sh http://my-server:8080
```

### Master Test Script

Test all running projects at once:

```bash
cd sample-projects

# Test all
./test-all.sh

# Test specific project
./test-all.sh webmvc
./test-all.sh webflux
./test-all.sh batch
```

### What Tests Verify

| Check | Description |
|-------|-------------|
| Application Running | HTTP endpoint responds |
| API Functionality | CRUD operations work |
| Traces Created | Spans appear in Jaeger |
| Service Spans | Correct service layer spans |
| Custom Attributes | `app.*` attributes present |
| Return Values | Return value attributes captured |

### Test Output Example

```
========================================
  Testing sample-spring-webmvc
========================================

[TEST] Checking if application is running...
[PASS] Application is running at http://localhost:8080
[TEST] === Testing Customer API ===
[TEST] Creating customer...
[PASS] Customer created with ID: 1
[TEST] Fetching customer by ID...
[PASS] Customer fetched successfully
...
[TEST] === Checking Traces in Jaeger ===
[PASS] Found 15 traces in Jaeger
[PASS] Found CustomerService spans
[PASS] Found OrderService spans
[PASS] Found custom attributes (app.customer.*)
[PASS] Found custom attributes (app.order.*)
```

---

## Understanding Traces

### Viewing Traces in Jaeger

1. Open http://localhost:16686
2. Select **Service** from dropdown (e.g., `sample-spring-webmvc`)
3. Click **Find Traces**
4. Click on a trace to see details

### Trace Structure

A typical trace for creating an order:

```
POST /api/orders
│
├── OrderController.createOrder
│   ├── code.namespace: com.otel.sample.webmvc.controller.OrderController
│   └── code.function: createOrder
│
├── OrderService.createOrder
│   ├── code.namespace: com.otel.sample.webmvc.service.impl.OrderServiceImpl
│   ├── code.function: createOrder
│   │
│   │   (attributes from arguments)
│   ├── app.order.customer_id: 1
│   ├── app.order.item_count: 2
│   │
│   │   (attributes from return value)
│   ├── app.order.created_id: 1
│   ├── app.order.order_number: ORD-2024-A1B2C3D4
│   ├── app.order.status: PENDING
│   ├── app.order.total: 65.98
│   │
│   ├── CustomerService.getCustomerById
│   │   └── app.customer.requested_id: 1
│   │
│   ├── PaymentService.calculateShipping
│   │   ├── app.shipping.zipcode: 10001
│   │   └── app.shipping.order_total: 59.98
│   │
│   └── OrderRepository.save
│
└── PaymentService.processPaymentAsync (async)
    ├── app.payment.order_id: 1
    ├── app.payment.amount: 65.98
    └── app.payment.method: CREDIT_CARD
```

### Attribute Types

| Attribute | Source | Example |
|-----------|--------|---------|
| `code.namespace` | Automatic | `com.otel.sample.webmvc.service.impl.OrderServiceImpl` |
| `code.function` | Automatic | `createOrder` |
| `code.instrumented.interface` | Automatic | `com.otel.sample.webmvc.service.OrderService` |
| `app.*` | Custom (arguments) | `app.order.customer_id` |
| `app.*` | Custom (return value) | `app.order.created_id` |

### Chained Method Call Attributes

When using chained method calls in configuration:

```json
{ "methodCall": "getCustomer.getAddress.getCity" }
```

The attribute will contain the final value from the chain:

```
app.customer_city: New York
```

---

## Customizing Instrumentation

### Modifying instrumentation.json

Each project has its own configuration file:

```
sample-projects/
├── sample-spring-webmvc/src/main/resources/instrumentation.json
├── sample-spring-webflux/src/main/resources/instrumentation.json
└── sample-spring-batch/src/main/resources/instrumentation.json
```

### Adding New Attributes

**Argument Attributes:**

```json
{
  "className": "com.otel.sample.webmvc.service.OrderService",
  "methodName": "createOrder",
  "attributes": [
    {
      "argIndex": 0,
      "methodCall": "getCustomerId",
      "attributeName": "app.order.customer_id"
    }
  ]
}
```

**Return Value Attributes:**

```json
{
  "className": "com.otel.sample.webmvc.service.OrderService",
  "methodName": "createOrder",
  "returnValueAttributes": [
    {
      "methodCall": "getId",
      "attributeName": "app.order.created_id"
    },
    {
      "methodCall": "getOrderNumber",
      "attributeName": "app.order.number"
    }
  ]
}
```

**Chained Method Calls:**

```json
{
  "attributes": [
    {
      "argIndex": 0,
      "methodCall": "getCustomer.getAddress.getCity",
      "attributeName": "app.customer_city"
    }
  ]
}
```

### Hot Reload

After modifying `instrumentation.json`, reload without restart:

```bash
# Using jcmd
jcmd <pid> JMX.invoke com.otel.dynamic:type=ConfigManager reloadConfiguration

# Or via JConsole
# Connect to the JVM → MBeans → com.otel.dynamic → ConfigManager → reloadConfiguration()
```

---

## Troubleshooting

### Application Won't Start

**Symptom:** Application fails to start

**Checks:**

1. Verify Java 21+ is being used:
   ```bash
   java -version
   ```

2. Check port is not in use:
   ```bash
   lsof -i :8080
   ```

3. Check extension JAR exists:
   ```bash
   ls -la target/dynamic-instrumentation-agent-1.1.0.jar
   ```

### No Traces in Jaeger

**Symptom:** Application runs but no traces appear

**Checks:**

1. Verify Jaeger is running:
   ```bash
   curl http://localhost:16686/api/services
   ```

2. Check OTLP endpoint:
   ```bash
   # Verify port 4317 is accessible
   nc -zv localhost 4317
   ```

3. Check application logs for OTel errors:
   ```bash
   # Look for errors in startup logs
   ```

### Spans Not Appearing for Custom Methods

**Symptom:** Some methods don't create spans

**Checks:**

1. Verify the class/method is in `instrumentation.json`

2. For package instrumentation, verify the class has the required annotation:
   ```bash
   # Check if class has @Service, @Repository, or @RestController
   ```

3. Check if the method is public and not excluded:
   - Constructors are excluded
   - `equals()`, `hashCode()`, `toString()` are excluded

### Custom Attributes Missing

**Symptom:** Spans appear but custom attributes are missing

**Checks:**

1. Verify `methodCall` matches an actual method name

2. For chained calls, verify each method in the chain exists

3. Check for null values in the chain - the entire attribute is skipped if any value is null

4. Enable debug logging:
   ```bash
   jcmd <pid> JMX.invoke com.otel.dynamic:type=ConfigManager setDebugEnabled true
   ```

### Test Script Fails

**Symptom:** Test script shows failures

**Common Issues:**

1. **Application not running:**
   ```
   [FAIL] Application is not running at http://localhost:8080
   ```
   → Start the application first

2. **Jaeger not running:**
   ```
   [FAIL] No traces found in Jaeger
   ```
   → Start Jaeger container

3. **Port conflicts:**
   → Check if another service is using the port

---

## Appendix: Full Configuration Examples

### WebMVC Complete Example

```json
{
  "packages": [
    {
      "packageName": "com.otel.sample.webmvc",
      "recursive": true,
      "annotations": [
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.web.bind.annotation.RestController"
      ]
    }
  ],
  "instrumentations": [
    {
      "className": "com.otel.sample.webmvc.service.CustomerService",
      "methodName": "createCustomer",
      "attributes": [
        { "argIndex": 0, "methodCall": "getEmail", "attributeName": "app.customer.email" },
        { "argIndex": 0, "methodCall": "getFirstName", "attributeName": "app.customer.first_name" },
        { "argIndex": 0, "methodCall": "getLastName", "attributeName": "app.customer.last_name" }
      ],
      "returnValueAttributes": [
        { "methodCall": "getId", "attributeName": "app.customer.created_id" },
        { "methodCall": "getEmail", "attributeName": "app.customer.created_email" }
      ]
    },
    {
      "className": "com.otel.sample.webmvc.service.OrderService",
      "methodName": "createOrder",
      "attributes": [
        { "argIndex": 0, "methodCall": "getCustomerId", "attributeName": "app.order.customer_id" },
        { "argIndex": 0, "methodCall": "getItems.size", "attributeName": "app.order.item_count" }
      ],
      "returnValueAttributes": [
        { "methodCall": "getId", "attributeName": "app.order.created_id" },
        { "methodCall": "getOrderNumber", "attributeName": "app.order.order_number" },
        { "methodCall": "getStatus.name", "attributeName": "app.order.status" },
        { "methodCall": "getTotal", "attributeName": "app.order.total" }
      ]
    }
  ]
}
```

---

## Next Steps

- Read [README.md](../README.md) for general documentation
- Read [ARCHITECTURE.md](../ARCHITECTURE.md) for technical details
- Read [ROADMAP.md](../ROADMAP.md) for planned features
