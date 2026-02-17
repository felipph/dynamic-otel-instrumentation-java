# Sample Spring Batch

A sample Spring Boot 3.4+ application demonstrating dynamic OpenTelemetry instrumentation with:
- **Spring Batch** - Chunk-oriented processing
- **Item Reader/Processor/Writer** - Classic batch pattern
- **Job Orchestration** - REST-triggered jobs

## Features Demonstrated

### 1. Item Reader Instrumentation
The `TransactionReader` is instrumented to trace each item read.

### 2. Item Processor Instrumentation
The `TransactionProcessor` validates and enriches transactions with full tracing.

### 3. Item Writer Instrumentation
The `TransactionWriter` traces chunk writes with batch size attributes.

### 4. Return Value Capture
Return values from reader/processor are captured as span attributes.

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
# Run the batch job
curl -X POST http://localhost:8082/api/batch/run

# Check processed transactions
curl http://localhost:8082/api/batch/processed

# Clear processed transactions and run again
curl -X DELETE http://localhost:8082/api/batch/processed
curl -X POST http://localhost:8082/api/batch/run
```

## Expected Spans

When you run a batch job, you should see spans like:

```
POST /api/batch/run
└── BatchController.runJob
    │
    └── TransactionStep (Spring Batch)
        │
        ├── TransactionReader.read (multiple times)
        │   ├── app.batch.txn_id = TXN-00001
        │   ├── app.batch.account = ACC-1
        │   └── app.batch.amount = 234.56
        │
        ├── TransactionProcessor.process (multiple times)
        │   ├── app.batch.processor.txn_id = TXN-00001
        │   ├── app.batch.processor.account = ACC-1
        │   ├── app.batch.processor.abs_amount = 234.56
        │   └── app.batch.processor.result_status = PROCESSED
        │
        └── TransactionWriter.write (chunk writes)
            └── app.batch.writer.chunk_size = 5
```

## Configuration

See `src/main/resources/instrumentation.json` for the instrumentation configuration.

## H2 Console

Access the H2 database console at http://localhost:8082/h2-console
- JDBC URL: `jdbc:h2:mem:testdb`
- Username: `sa`
- Password: (empty)
