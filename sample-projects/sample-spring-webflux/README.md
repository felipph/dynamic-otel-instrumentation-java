# Sample Spring WebFlux

A sample Spring Boot 3.4+ application demonstrating dynamic OpenTelemetry instrumentation with:
- **Spring WebFlux** - Reactive REST Controllers
- **Spring Data R2DBC** - Reactive database access
- **Project Reactor** - Mono/Flux patterns
- **Virtual Threads** - Java 21+

## Features Demonstrated

### 1. Reactive Service Instrumentation
Service methods return `Mono<T>` and `Flux<T>` - all are traced.

### 2. Package-Level Instrumentation
All classes with `@Service`, `@Repository`, or `@RestController` are instrumented.

### 3. Reactive Streams
Demonstrates instrumentation with non-blocking reactive streams.

## Prerequisites

- Java 21+
- Maven 3.6+
- OpenTelemetry Collector or Jaeger

## Quick Start

```bash
# Start Jaeger (if needed)
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  jaegertracing/all-in-one:latest

# Start the application
./scripts/start.sh
```

## Test the API

```bash
# Create a product
curl -X POST http://localhost:8081/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "REACT-001",
    "name": "Reactive Product",
    "description": "A reactive product",
    "price": 49.99,
    "stockQuantity": 100
  }'

# Get all products
curl http://localhost:8081/api/products

# Get available products
curl http://localhost:8081/api/products/available

# Update stock
curl -X PATCH "http://localhost:8081/api/products/1/stock?quantity=-5"
```

## View Traces

Open Jaeger at http://localhost:16686 and search for `sample-spring-webflux`.

## Configuration

See `src/main/resources/instrumentation.json` for the instrumentation configuration.
