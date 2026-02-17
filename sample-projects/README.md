# Sample Projects

This directory contains sample Spring Boot projects demonstrating the dynamic OpenTelemetry instrumentation extension.

> **Note:** These projects require **Java 21** to compile (due to Lombok and Virtual Threads support). If you have Java 25+, you may need to set `JAVA_HOME` to a Java 21 installation.

## Projects Overview

| Project | Description | Port | Technologies |
|---------|-------------|------|--------------|
| [sample-spring-webmvc](./sample-spring-webmvc) | Traditional Spring MVC REST API | 8080 | Spring Web MVC, JPA, Async, Virtual Threads |
| [sample-spring-webflux](./sample-spring-webflux) | Reactive Spring WebFlux API | 8081 | WebFlux, R2DBC, Reactor, Virtual Threads |
| [sample-spring-batch](./sample-spring-batch) | Spring Batch job processing | 8082 | Spring Batch, Chunk Processing |

## Features Demonstrated

### All Projects
- **Package-level instrumentation** - Auto-instrument `@Service`, `@Repository`, `@RestController`
- **Interface-based instrumentation** - Configure interface, instrument all implementations
- **Return value capture** - Extract attributes from method return values
- **Chained method calls** - Navigate object graphs (e.g., `getCustomer.getAddress.getCity`)

### Spring WebMVC
- REST Controllers with CRUD operations
- Async methods with Virtual Threads
- Service layer with business logic
- Payment processing (async)

### Spring WebFlux
- Reactive REST Controllers
- R2DBC reactive database access
- Mono/Flux patterns
- Non-blocking processing

### Spring Batch
- Chunk-oriented processing
- Item Reader/Processor/Writer pattern
- Job orchestration via REST API
- Batch attribute extraction

## Prerequisites

- **Java 21+** (for Virtual Threads support)
- **Maven 3.6+**
- **Docker** (for Jaeger/OTel Collector)

## Quick Start

### 1. Build the Extension

```bash
# From the project root
./scripts/build.sh
# or
mvn clean package -DskipTests
```

### 2. Start Jaeger

```bash
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  jaegertracing/all-in-one:latest
```

### 3. Run a Sample Project

```bash
# WebMVC (port 8080)
cd sample-projects/sample-spring-webmvc
./scripts/start.sh

# WebFlux (port 8081)
cd sample-projects/sample-spring-webflux
./scripts/start.sh

# Batch (port 8082)
cd sample-projects/sample-spring-batch
./scripts/start.sh
```

### 4. View Traces

Open Jaeger UI: http://localhost:16686

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_ENDPOINT` | `http://localhost:4317` | OTLP collector endpoint |
| `SERVICE_NAME` | (project-specific) | Service name in traces |

## Sample API Calls

### WebMVC (port 8080)

```bash
# Create customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"firstName":"John","lastName":"Doe","email":"john@example.com"}'

# Create order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"items":[{"productName":"Product","sku":"SKU-001","quantity":1,"unitPrice":29.99}]}'
```

### WebFlux (port 8081)

```bash
# Create product
curl -X POST http://localhost:8081/api/products \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-001","name":"Product","price":29.99,"stockQuantity":100}'

# Get available products
curl http://localhost:8081/api/products/available
```

### Batch (port 8082)

```bash
# Run batch job
curl -X POST http://localhost:8082/api/batch/run

# Get processed transactions
curl http://localhost:8082/api/batch/processed
```

## Instrumentation Configuration

Each project has its own `instrumentation.json` in `src/main/resources/` demonstrating:

1. **Argument attributes** - Extract from method parameters
2. **Return value attributes** - Extract from method return values
3. **Chained calls** - Navigate nested objects
4. **Package-level** - Auto-instrument by annotation

## Running Tests

Each project includes a test script that:
1. Makes API calls to trigger instrumented methods
2. Verifies responses
3. Checks Jaeger for traces and custom attributes

```bash
# Test individual projects
cd sample-projects/sample-spring-webmvc && ./scripts/test.sh
cd sample-projects/sample-spring-webflux && ./scripts/test.sh
cd sample-projects/sample-spring-batch && ./scripts/test.sh

# Or test all from parent directory
cd sample-projects && ./test-all.sh

# Test specific project
./test-all.sh webmvc
./test-all.sh webflux
./test-all.sh batch
```

### What the Tests Verify

| Check | Description |
|-------|-------------|
| API Responses | Basic CRUD operations work |
| Spans Created | Traces appear in Jaeger |
| Service Spans | CustomerService, OrderService, etc. |
| Custom Attributes | `app.customer.*`, `app.order.*`, etc. |
| Return Values | Return value attributes captured |
| Chained Calls | Nested method calls traced |

## Expected Trace Structure

```
HTTP Request
└── Controller.method
    └── Service.method
        ├── app.xxx.attribute = value (from arguments)
        │
        │  (return value attributes - added on exit)
        ├── app.xxx.result_id = 123
        ├── app.xxx.result_status = SUCCESS
        │
        └── Repository.method
            └── app.xxx.entity_id = 123
```
