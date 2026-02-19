# Sample Spring Web MVC

A sample Spring Boot 3.4+ application demonstrating dynamic OpenTelemetry instrumentation with:
- **Spring Web MVC** - REST Controllers
- **Spring Data JPA** - Repository pattern
- **Async Methods** - With Virtual Threads (Java 21+)
- **Service Layer** - Business logic instrumentation

## Features Demonstrated

### 1. Method-Level Instrumentation
All service methods are traced with custom attributes extracted from:
- Method arguments (using `attributes` config)
- Return values (using `returnValueAttributes` config)
- Chained method calls (e.g., `getItems.size`)

### 2. Package-Level Instrumentation
All classes annotated with `@Service`, `@Repository`, or `@RestController` are automatically instrumented.

### 3. Interface-Based Instrumentation
Services implement interfaces - configuring the interface instruments all implementations.

### 4. Async Processing
Payment processing demonstrates async method instrumentation with Virtual Threads.

## Prerequisites

- Java 21+
- Maven 3.6+
- OpenTelemetry Collector or Jaeger (for trace visualization)

## Quick Start

### 1. Build and Start Jaeger (if needed)

```bash
# From the main project directory
docker run -d --name jaeger \
  -p 16686:16686 \
  -p 4317:4317 \
  -p 4318:4318 \
  jaegertracing/all-in-one:latest
```

### 2. Start the Application

```bash
# Using the startup script
./scripts/start.sh

# Or manually
mvn clean package -DskipTests
java \
  -javaagent:/path/to/opentelemetry-javaagent.jar \
  -javaagent:../../target/dynamic-instrumentation-agent-1.1.0.jar \
  -Dotel.javaagent.extensions=../../target/dynamic-instrumentation-agent-1.1.0.jar \
  -Dinstrumentation.config.path=src/main/resources/instrumentation.json \
  -Dotel.service.name=sample-spring-webmvc \
  -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
  -jar target/sample-spring-webmvc-1.0.0.jar
```

### 3. Test the API

```bash
# Create a customer
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@example.com",
    "phone": "+1-555-0100",
    "address": {
      "street": "123 Main St",
      "city": "New York",
      "state": "NY",
      "zipCode": "10001",
      "country": "USA"
    }
  }'

# Get customer by ID
curl http://localhost:8080/api/customers/1

# Create a product
curl -X POST http://localhost:8080/api/products \
  -H "Content-Type: application/json" \
  -d '{
    "sku": "PROD-001",
    "name": "Sample Product",
    "description": "A sample product for testing",
    "price": 29.99,
    "stockQuantity": 100
  }'

# Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": 1,
    "items": [
      {
        "productName": "Sample Product",
        "sku": "PROD-001",
        "quantity": 2,
        "unitPrice": 29.99
      }
    ]
  }'

# Process payment (async)
curl -X POST "http://localhost:8080/api/payments/process?orderId=1&amount=65.98&paymentMethod=CREDIT_CARD"
```

### 4. View Traces

Open Jaeger UI at http://localhost:16686 and search for `sample-spring-webmvc`.

## Expected Spans

When you create an order, you should see spans like:

```
POST /api/orders
└── OrderController.createOrder
    └── OrderService.createOrder
        ├── app.order.customer_id = 1
        ├── app.order.item_count = 1
        │
        │  (return value attributes)
        ├── app.order.created_id = 1
        ├── app.order.order_number = ORD-2024-XXXXXXXX
        ├── app.order.status = PENDING
        ├── app.order.total = 65.98
        │
        ├── CustomerService.getCustomerById
        │   └── app.customer.requested_id = 1
        │
        ├── PaymentService.calculateShipping
        │   ├── app.shipping.zipcode = 10001
        │   └── app.shipping.order_total = 59.98
        │
        └── PaymentService.processPaymentAsync (async)
            ├── app.payment.order_id = 1
            ├── app.payment.amount = 65.98
            └── app.payment.method = CREDIT_CARD
```

## H2 Console

Access the H2 database console at http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (empty)

## Configuration

See `src/main/resources/instrumentation.json` for the instrumentation configuration.

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_ENDPOINT` | `http://localhost:4317` | OTLP collector endpoint |
| `SERVICE_NAME` | `sample-spring-webmvc` | Service name in traces |
