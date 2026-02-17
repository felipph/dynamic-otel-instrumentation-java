# Sample Microservices

This project demonstrates a microservices architecture with HTTP and messaging communication, instrumented with OpenTelemetry.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              HTTP Request                                    │
│                                  │                                          │
│                                  ▼                                          │
│  ┌─────────────────────┐                    ┌──────────────────────────┐    │
│  │   microservice-1    │                    │      microservice-2      │    │
│  │   (Port 8080)       │                    │      (Port 8081)         │    │
│  │                     │                    │                          │    │
│  │  OrderController ───┼───┐                │  OrderMessageConsumer    │    │
│  │  OrderService       │   │                │  OrderService.processOrder│   │
│  │  OrderRepository    │   │                │  OrderRepository         │    │
│  └─────────────────────┘   │                └──────────────────────────┘    │
│                            │                           ▲                     │
│                            ▼                           │                     │
│                    ┌───────────────┐                   │                     │
│                    │   RabbitMQ    │───────────────────┘                     │
│                    │ orders.queue  │                                         │
│                    └───────────────┘                                         │
│                            │                                                 │
└────────────────────────────┼─────────────────────────────────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  OTel Collector │
                    │   (Port 4317)   │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │     Jaeger      │
                    │   (Port 16686)  │
                    └─────────────────┘
```

## Flow

1. **HTTP Request** → `microservice-1` receives order via REST API
2. **Order Creation** → `OrderService.createOrder()` validates and saves order
3. **Message Publishing** → Order is sent to RabbitMQ `orders.queue`
4. **Message Consumption** → `microservice-2` (or any instance) picks up the message
5. **Order Processing** → `OrderService.processOrder()` simulates business logic

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.6+
- Docker & Docker Compose (for full stack)

### Option 1: Run Locally (Single Instance)

First, start the infrastructure:

```bash
# Start RabbitMQ and Jaeger
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management
docker run -d --name jaeger -p 16686:16686 -p 4317:4317 -p 4318:4318 jaegertracing/all-in-one:1.58
```

Then run the application:

```bash
./scripts/run.sh 8080
```

### Option 2: Full Docker Compose Stack (Two Instances)

This starts two microservice instances, RabbitMQ, Jaeger, and OTel Collector:

```bash
./scripts/docker.sh
```

## Testing

### Test Single Instance

```bash
./scripts/test.sh http://localhost:8080
```

### Test Docker Stack

```bash
./scripts/test-docker.sh
```

### Manual API Calls

```bash
# Create an order
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId":"CUST-001","productId":"PROD-001","quantity":2,"unitPrice":999.99}'

# Get all orders
curl http://localhost:8080/api/orders

# Get specific order
curl http://localhost:8080/api/orders/{orderId}

# Get processing stats
curl http://localhost:8080/api/orders/stats

# Health check
curl http://localhost:8080/api/health
```

## Instrumentation

The `config/instrumentation.json` file configures instrumentation for:

### HTTP Layer
- `OrderController.createOrder` - Captures customer_id, product_id, quantity from request

### Service Layer
- `OrderService.createOrder` - Captures input and output attributes
- `OrderService.processOrder` - Captures processed_by to show which instance processed

### Messaging Layer
- `OrderMessageProducer.sendOrder` - Captures order details when publishing
- `OrderMessageConsumer.receiveOrder` - Captures order details when consuming

### Repository Layer
- `OrderRepository.save` - Captures order_id and status

## Viewing Traces

1. Open Jaeger UI: http://localhost:16686
2. Select service: `order-service-1` or `order-service-2`
3. Search for traces

### Expected Trace Structure

```
POST /api/orders (HTTP span)
└── OrderController.createOrder
    ├── app.customer_id = CUST-001
    ├── app.product_id = PROD-001
    ├── app.quantity = 2
    │
    └── OrderService.createOrder
        ├── app.input.customer_id = CUST-001
        ├── app.input.product_id = PROD-001
        ├── app.output.order_id = xxx-xxx-xxx
        ├── app.output.status = CREATED
        │
        ├── OrderRepository.save
        │   └── db.order_id = xxx-xxx-xxx
        │
        └── OrderMessageProducer.sendOrder
            └── messaging.order_id = xxx-xxx-xxx

OrderMessageConsumer.receiveOrder (separate trace, may be on different instance)
└── messaging.order_id = xxx-xxx-xxx
└── messaging.customer_id = CUST-001
│
└── OrderService.processOrder
    ├── app.input.order_id = xxx-xxx-xxx
    ├── app.output.processed_by = order-service-2  <-- Shows which instance
    │
    └── OrderRepository.save
```

## RabbitMQ Management

Access the RabbitMQ management UI at http://localhost:15672

- Username: `guest`
- Password: `guest`

You can see:
- Queues and message rates
- Connections from both microservice instances
- Message publishing/consuming statistics

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | 8080 | Server port |
| `SPRING_APPLICATION_NAME` | sample-microservices | Application name |
| `SPRING_RABBITMQ_HOST` | localhost | RabbitMQ host |
| `OTEL_SERVICE_NAME` | order-service | Service name in traces |

## Ports

| Service | Port | Description |
|---------|------|-------------|
| microservice-1 | 8080 | First instance |
| microservice-2 | 8081 | Second instance |
| RabbitMQ | 5672 | AMQP protocol |
| RabbitMQ Mgmt | 15672 | Management UI |
| Jaeger | 16686 | Trace UI |
| OTel Collector | 4317 | OTLP gRPC |
| OTel Collector | 4318 | OTLP HTTP |
