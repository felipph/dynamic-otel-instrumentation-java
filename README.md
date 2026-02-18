# Dynamic OpenTelemetry Instrumentation Extension

A **configuration-driven** extension for the [OpenTelemetry Java Agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation) that lets you instrument any Java application — without modifying source code — by editing a single JSON file.

---

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Configuration Reference](#configuration-reference)
4. [Instrumentation Modes](#instrumentation-modes)
5. [Advanced Attribute Extraction](#advanced-attribute-extraction)
6. [Span Attributes](#span-attributes)
7. [Deployment with Docker](#deployment-with-docker)
8. [JMX Management](#jmx-management)
9. [Troubleshooting](#troubleshooting)
10. [Full Examples](#full-examples)
11. [Sample Projects](#sample-projects)
12. [Further Reading](#further-reading)

---

## Overview

### What It Does

This project builds a JAR that plugs into the official **OpenTelemetry Java Agent** as an extension. At startup it reads `instrumentation.json` and dynamically instruments your application classes using ByteBuddy, creating OpenTelemetry spans for every matched method.

| Feature | Description |
|---------|-------------|
| **100% Config-Driven** | All instrumentation defined in `instrumentation.json` |
| **Zero Code Changes** | No modifications to the target application |
| **Method-Level Instrumentation** | Target specific classes and methods with custom attribute extraction |
| **Package-Level Instrumentation** | Instrument all classes in a package, optionally filtered by annotations |
| **Interface-Aware** | Configure an interface — all implementations are instrumented automatically |
| **Interface Detection** | Spans carry a `code.instrumented.interface` attribute when the match came from an interface |
| **Custom Attribute Extraction** | Extract method arguments into span attributes via reflection |
| **Chained Method Calls** | Navigate object graphs with `getCustomer.getAddress.getCity` syntax |
| **Return Value Capture** | Extract attributes from method return values |
| **ClassLoader Safe** | Runs as an OTel Agent extension — classloader isolation handled automatically |
| **JMX Management** | Runtime control via JMX MBeans |
| **Incremental Hot Reload** | Only retransform classes affected by config changes — fast even with 4k+ classes |

### How It Works (High Level)

```
┌─────────────────────────────────────────────────────────────┐
│  JVM Startup                                                │
│                                                             │
│  1. OTel Java Agent loads (-javaagent)                      │
│  2. Agent discovers this extension via SPI                  │
│  3. Extension reads instrumentation.json                    │
│  4. Creates TypeInstrumentation instances per class/package │
│  5. ByteBuddy injects advice into matched methods           │
│  6. Every call to an instrumented method creates a Span     │
│  7. Spans are exported via OTLP to your collector           │
└─────────────────────────────────────────────────────────────┘
```

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Java JDK | 8+ |
| Maven | 3.6+ |
| OpenTelemetry Java Agent | 2.25.0+ |
| An OTLP-compatible backend | Jaeger, SigNoz, Grafana Tempo, etc. |

---

## Quick Start

### 1. Build the Extension

```bash
# Using the build script
bash scripts/build.sh

# Or directly with Maven
mvn clean package -DskipTests
```

This produces `target/dynamic-instrumentation-agent-1.0.0.jar`.

### 2. Create Your Configuration

Create an `instrumentation.json` file:

```json
{
  "packages": [
    {
      "packageName": "com.myapp.service",
      "recursive": true,
      "annotations": [
        "org.springframework.stereotype.Service"
      ]
    }
  ],
  "instrumentations": [
    {
      "className": "com.myapp.controller.OrderController",
      "methodName": "createOrder",
      "attributes": [
        {
          "argIndex": 0,
          "methodCall": "getCustomerId",
          "attributeName": "app.customer_id"
        }
      ]
    }
  ]
}
```

### 3. Run Your Application

To enable **hot reload** (runtime configuration updates without restart), the extension JAR must be added as a `-javaagent` (so it can capture the JVM `Instrumentation` instance) AND as an OTel extension.

```bash
java \
  -javaagent:/path/to/opentelemetry-javaagent.jar \
  -javaagent:/path/to/dynamic-instrumentation-agent-1.0.0.jar \
  -Dotel.javaagent.extensions=/path/to/dynamic-instrumentation-agent-1.0.0.jar \
  -Dinstrumentation.config.path=/path/to/instrumentation.json \
  -Dotel.service.name=my-application \
  -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
  -Dotel.exporter.otlp.protocol=grpc \
  -jar my-application.jar
```

> **Note:** The second `-javaagent` flag is required for hot reload functionality. Without it, the extension will still work for static instrumentation but `reloadConfiguration()` via JMX will not be able to retransform already-loaded classes.

### 4. View Traces

Open your tracing backend (e.g., Jaeger at `http://localhost:16686`) and search for your service name.

---

## Configuration Reference

The configuration file (`instrumentation.json`) has two top-level sections:

```json
{
  "packages": [ ... ],
  "instrumentations": [ ... ]
}
```

### `packages` — Package-Level Instrumentation

Each entry instruments **all public methods** of all classes in a package.

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `packageName` | String | **Yes** | — | Fully qualified package name (e.g., `com.myapp.service`) |
| `recursive` | boolean | No | `false` | If `true`, includes all sub-packages |
| `annotations` | String[] | No | `[]` | If non-empty, only classes annotated with at least one of these are instrumented |

**Example — Instrument all Spring services recursively:**

```json
{
  "packages": [
    {
      "packageName": "com.myapp",
      "recursive": true,
      "annotations": [
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository",
        "org.springframework.web.bind.annotation.RestController"
      ]
    }
  ]
}
```

**Example — Instrument everything in a single package (no filtering):**

```json
{
  "packages": [
    {
      "packageName": "com.myapp.service",
      "recursive": false
    }
  ]
}
```

### `instrumentations` — Method-Level Instrumentation

Each entry targets a **specific class and method** with optional custom attribute extraction.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `className` | String | **Yes** | Fully qualified class or interface name |
| `methodName` | String | **Yes** | Method name to instrument |
| `attributes` | AttributeDefinition[] | No | Custom attributes to extract from method arguments |
| `returnValueAttributes` | ReturnValueAttribute[] | No | Attributes to extract from the method's return value |

#### `AttributeDefinition`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `argIndex` | int | **Yes** | 0-based index of the method argument |
| `methodCall` | String | No | Method to invoke on the argument. Supports **chained calls** with dot notation (e.g., `getCustomer.getAddress.getCity`). If omitted or `"toString"`, uses `arg.toString()` |
| `attributeName` | String | **Yes** | Name of the span attribute to set |

#### `ReturnValueAttribute`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `methodCall` | String | No | Method to invoke on the return value. Supports **chained calls** with dot notation (e.g., `getId`). If omitted or `"toString"`, uses `returnValue.toString()` |
| `attributeName` | String | **Yes** | Name of the span attribute to set |

**Example — Extract customer ID from first argument:**

```json
{
  "instrumentations": [
    {
      "className": "com.myapp.service.OrderService",
      "methodName": "createOrder",
      "attributes": [
        {
          "argIndex": 0,
          "methodCall": "getCustomerId",
          "attributeName": "app.customer_id"
        },
        {
          "argIndex": 0,
          "methodCall": "getPaymentMethod",
          "attributeName": "app.payment_method"
        }
      ]
    }
  ]
}
```

**Example — Use a primitive/String argument directly (no methodCall):**

```json
{
  "attributes": [
    { "argIndex": 0, "attributeName": "app.product_id" }
  ]
}
```

**Example — Chained method calls on argument:**

```json
{
  "instrumentations": [
    {
      "className": "com.myapp.service.OrderService",
      "methodName": "createOrder",
      "attributes": [
        { "argIndex": 0, "methodCall": "getCustomer.getAddress.getCity", "attributeName": "app.customer_city" },
        { "argIndex": 0, "methodCall": "getCustomer.getAddress.getState", "attributeName": "app.customer_state" }
      ]
    }
  ]
}
```

**Example — Extract attributes from return value:**

```json
{
  "instrumentations": [
    {
      "className": "com.myapp.service.OrderService",
      "methodName": "createOrder",
      "returnValueAttributes": [
        { "methodCall": "getId", "attributeName": "app.order_id" },
        { "methodCall": "getOrderNumber", "attributeName": "app.order_number" },
        { "methodCall": "getStatus.name", "attributeName": "app.order_status" }
      ]
    }
  ]
}
```

**Example — Combined: argument attributes + return value attributes:**

```json
{
  "instrumentations": [
    {
      "className": "com.myapp.service.OrderService",
      "methodName": "createOrder",
      "attributes": [
        { "argIndex": 0, "methodCall": "getCustomerId", "attributeName": "app.customer_id" },
        { "argIndex": 0, "methodCall": "getCustomer.getAddress.getCity", "attributeName": "app.customer_city" }
      ],
      "returnValueAttributes": [
        { "methodCall": "getId", "attributeName": "app.order_id" },
        { "methodCall": "getTotalAmount", "attributeName": "app.order_total" }
      ]
    }
  ]
}
```

---

## Instrumentation Modes

### Mode 1: Method-Level (Precise)

Target specific methods on specific classes. Supports custom attribute extraction.

```json
{
  "instrumentations": [
    {
      "className": "com.myapp.service.OrderService",
      "methodName": "processOrder",
      "attributes": [
        { "argIndex": 0, "attributeName": "app.order_id" }
      ]
    }
  ]
}
```

**When to use:** You need fine-grained control over which methods are traced and want to extract business-relevant attributes from method arguments.

### Mode 2: Interface-Level (Polymorphic)

Set `className` to an **interface** — all implementing classes are automatically instrumented.

```json
{
  "instrumentations": [
    {
      "className": "com.myapp.service.IOrderService",
      "methodName": "createOrder",
      "attributes": [
        { "argIndex": 0, "methodCall": "getCustomerId", "attributeName": "app.customer_id" }
      ]
    }
  ]
}
```

This instruments `createOrder` on **every class that implements `IOrderService`** (e.g., `OrderServiceImpl`, `OrderServiceMock`, etc.).

When a span is generated from an interface match, the span will carry:
- `code.instrumented.interface` = `com.myapp.service.IOrderService`

**When to use:** You have interfaces with multiple implementations (common in Spring/EJB) and want to instrument all of them with a single config entry.

### Mode 3: Package-Level (Broad)

Instrument all classes in a package. Optionally filter by annotations.

```json
{
  "packages": [
    {
      "packageName": "com.myapp.service",
      "recursive": true,
      "annotations": [
        "org.springframework.stereotype.Service"
      ]
    }
  ]
}
```

**When to use:** You want broad coverage across an entire package or layer of your application without listing every class.

### Combining Modes

All three modes can be used together in the same configuration file. Method-level rules take precedence for attribute extraction — if a method matches both a package rule and a method-level rule, the method-level attributes are extracted.

```json
{
  "packages": [
    {
      "packageName": "com.myapp",
      "recursive": true,
      "annotations": ["org.springframework.stereotype.Service"]
    }
  ],
  "instrumentations": [
    {
      "className": "com.myapp.service.OrderService",
      "methodName": "createOrder",
      "attributes": [
        { "argIndex": 0, "methodCall": "getCustomerId", "attributeName": "app.customer_id" }
      ]
    }
  ]
}
```

In this example:
- **All `@Service` classes** in `com.myapp` get basic span creation (class name, method name)
- **`OrderService.createOrder`** additionally extracts `app.customer_id` from the first argument

---

## Advanced Attribute Extraction

### Chained Method Calls

The `methodCall` field supports **dot notation** for navigating object graphs. This allows you to traverse nested objects to extract deeply nested values.

**Syntax:** `method1.method2.method3` invokes methods sequentially, where each method is called on the result of the previous one.

**Example:** Extract the customer's city from a nested address object:

```json
{
  "attributes": [
    { "argIndex": 0, "methodCall": "getCustomer.getAddress.getCity", "attributeName": "app.customer_city" }
  ]
}
```

This is equivalent to: `orderRequest.getCustomer().getAddress().getCity()`

**Error Handling:** If any method in the chain returns `null` or throws an exception, the attribute is silently skipped (no error is propagated to your application).

### Return Value Capture

The `returnValueAttributes` array lets you extract attributes from the value returned by the instrumented method. This is useful for capturing IDs, status codes, or other data generated during method execution.

**Example:** Capture order ID and status from the returned Order object:

```json
{
  "instrumentations": [
    {
      "className": "com.myapp.service.OrderService",
      "methodName": "createOrder",
      "returnValueAttributes": [
        { "methodCall": "getId", "attributeName": "app.order_id" },
        { "methodCall": "getStatus.name", "attributeName": "app.order_status" }
      ]
    }
  ]
}
```

**Note:** Return value attributes are added to the span **after** the method completes (in the `@Advice.OnMethodExit` phase). If the method throws an exception, no return value attributes are captured.

**Combining with Argument Attributes:**

```json
{
  "instrumentations": [
    {
      "className": "com.myapp.service.OrderService",
      "methodName": "processPayment",
      "attributes": [
        { "argIndex": 0, "methodCall": "getOrderId", "attributeName": "app.input.order_id" },
        { "argIndex": 0, "methodCall": "getAmount", "attributeName": "app.input.amount" }
      ],
      "returnValueAttributes": [
        { "methodCall": "getTransactionId", "attributeName": "app.output.transaction_id" },
        { "methodCall": "isApproved", "attributeName": "app.output.approved" }
      ]
    }
  ]
}
```

---

## Span Attributes

Every instrumented method produces a span with these **automatic attributes**:

| Attribute | Description | Example |
|-----------|-------------|---------|
| `code.namespace` | Fully qualified class name | `com.myapp.service.OrderService` |
| `code.function` | Method name | `createOrder` |
| `code.instrumented.interface` | *(Only if matched via interface)* The interface name | `com.myapp.service.IOrderService` |

Plus any **custom attributes** defined in the `attributes` array (extracted from method arguments) and `returnValueAttributes` array (extracted from the return value) of the method-level config.

### Span Naming

Spans are named `SimpleClassName.methodName`, e.g.:
- `OrderService.createOrder`
- `CustomerRepository.findById`

### Interface Detection

The `code.instrumented.interface` attribute is set in two scenarios:

1. **Method-level instrumentation via interface:** When `className` in the config is an interface, and the method executes on an implementing class, the span carries the interface name.

2. **Package-level instrumentation:** When a method is instrumented via a package rule and the method is declared by an interface the class implements, the span carries that interface name.

This makes it easy to filter traces in your backend to find all spans that originated from interface-based instrumentation.

---

## Deployment with Docker

### Architecture

```
┌──────────────┐                              ┌─────────────┐
│  Application │─────────── OTLP ────────────▶│   Jaeger    │
│  (JBoss/     │                              │  (All-in-   │
│   Tomcat/    │                              │   One)      │
│   Spring)    │                              │  :16686 UI  │
│              │                              └─────────────┘
│              │
│  + OTel Agent│
│  + Extension │
└──────────────┘
```

### JBoss / WildFly

The `standalone.conf` must set the `-javaagent` flag (WildFly overrides `JAVA_OPTS`):

```bash
# docker/configs/standalone.conf
JAVA_OPTS="$JAVA_OPTS -javaagent:/opt/jboss/agents/opentelemetry-javaagent.jar"
JAVA_OPTS="$JAVA_OPTS -javaagent:/opt/jboss/agents/dynamic-instrumentation-extension.jar"
JAVA_OPTS="$JAVA_OPTS -Dotel.javaagent.extensions=/opt/jboss/agents/dynamic-instrumentation-extension.jar"
```

In `docker-compose.yml`:

```yaml
jboss:
  environment:
    JAVA_OPTS_APPEND: >-
      -Dotel.service.name=my-application
      -Dinstrumentation.config.path=/opt/otel/config/instrumentation.json
    OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4317
    OTEL_EXPORTER_OTLP_PROTOCOL: grpc
  volumes:
    - ./opentelemetry-javaagent.jar:/opt/jboss/agents/opentelemetry-javaagent.jar:ro
    - ../target/dynamic-instrumentation-agent-1.0.0.jar:/opt/jboss/agents/dynamic-instrumentation-extension.jar:ro
    - ./configs/instrumentation.json:/opt/otel/config/instrumentation.json:ro
    - ./configs/standalone.conf:/opt/jboss/wildfly/bin/standalone.conf:ro
```

### Tomcat

```bash
# setenv.sh
export CATALINA_OPTS="$CATALINA_OPTS -javaagent:/opt/agents/opentelemetry-javaagent.jar"
export CATALINA_OPTS="$CATALINA_OPTS -Dotel.javaagent.extensions=/opt/agents/dynamic-instrumentation-extension.jar"
export CATALINA_OPTS="$CATALINA_OPTS -Dinstrumentation.config.path=/opt/config/instrumentation.json"
export CATALINA_OPTS="$CATALINA_OPTS -Dotel.service.name=my-tomcat-app"
```

### Spring Boot

```bash
java \
  -javaagent:/opt/agents/opentelemetry-javaagent.jar \
  -Dotel.javaagent.extensions=/opt/agents/dynamic-instrumentation-extension.jar \
  -Dinstrumentation.config.path=/opt/config/instrumentation.json \
  -Dotel.service.name=my-spring-app \
  -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
  -jar my-spring-app.jar
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `OTEL_SERVICE_NAME` | — | Service name shown in traces |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | OTLP collector endpoint |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | `grpc` | Protocol: `grpc` or `http/protobuf` |
| `OTEL_EXPORTER_OTLP_TIMEOUT` | `30000` | Export timeout in milliseconds |

### System Properties

| Property | Default | Description |
|----------|---------|-------------|
| `instrumentation.config.path` | `/opt/otel/config/instrumentation.json` | Path to the JSON config file |
| `otel.javaagent.extensions` | — | Path to this extension JAR |

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `INSTRUMENTATION_CONFIG_PATH` | `/opt/otel/config/instrumentation.json` | Alternative way to specify config path (overrides default, but system property takes precedence) |

---

## JMX Management

The extension registers a JMX MBean at `com.otel.dynamic:type=ConfigManager`.

> **Note:** MBean registration is deferred by ~30 seconds after JVM startup to allow application servers (like WildFly/JBoss) to fully initialize. This avoids classloader conflicts during the startup phase.

### Operations

| Operation | Description |
|-----------|-------------|
| `reloadConfiguration()` | Reload `instrumentation.json` from disk and retransform classes |
| `setDebugEnabled(boolean)` | Enable/disable debug logging |

### Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `ConfigFilePath` | String | Path to the config file |
| `DebugEnabled` | boolean | Whether debug logging is on |
| `InstrumentationCount` | int | Number of method-level rules |
| `InstrumentedClassCount` | int | Number of instrumented classes |

### Hot Reload via JMX

When you call `reloadConfiguration()`, the extension:

1. **Snapshots** current instrumentation checksums
2. **Reloads** the JSON configuration from disk
3. **Updates** the internal registry with new instrumentation rules
4. **Computes diff** between old and new configurations
5. **Retransforms only affected classes** (new, changed, or removed rules)

This allows you to add, remove, or modify instrumentation rules at runtime without restarting the application.

#### Incremental Retransformation

The extension uses **checksum-based incremental retransformation** to minimize the performance impact of hot reloads in large applications:

| Scenario | Classes Retransformed |
|----------|----------------------|
| Add 1 new method rule | ~1 class |
| Change 1 attribute | ~1 class |
| Reload unchanged config | 0 classes |
| Remove a rule | affected classes only |

**How it works:**
- Each instrumentation rule (class#method) is assigned an MD5 checksum based on its attributes
- Before reload, current checksums are snapshotted
- After reload, the diff is computed to identify added, changed, removed, and unchanged entries
- Only classes with affected rules are retransformed

This optimization makes hot reload practical even in applications with thousands of instrumented classes.

### Access via JConsole

1. Open JConsole and connect to your Java process
2. Navigate to **MBeans** → `com.otel.dynamic` → `ConfigManager`
3. Invoke operations or read attributes

### Access via Command Line

```bash
# Using jcmd (recommended)
jcmd <pid> JMX.invoke com.otel.dynamic:type=ConfigManager reloadConfiguration

# Enable debug logging
jcmd <pid> JMX.invoke com.otel.dynamic:type=ConfigManager setDebugEnabled true
```

---

## Troubleshooting

### Agent Not Loading

**Symptom:** No `[DynamicInstrumentation]` messages in logs.

**Checks:**
- Verify `-javaagent` path is correct
- For WildFly/JBoss: the flag **must** be in `standalone.conf`, not just `JAVA_OPTS_APPEND`
- Verify `-Dotel.javaagent.extensions` points to the extension JAR

### No Spans Appearing

**Symptom:** Agent loads but no traces in your backend.

**Checks:**
- Verify `instrumentation.json` has correct fully-qualified class names
- Verify the OTLP endpoint is reachable from the application container
- Check protocol: OTel agent v2.25.0+ defaults to `http/protobuf` (port 4318). Use `grpc` (port 4317) if your collector expects it
- Check logs for `Dynamic instrumentation: N classes configured`

### ClassNotFoundException in Advice

**Symptom:** `ClassNotFoundException` for `DynamicInstrumentationConfig` or `DynamicAdvice`.

**Cause:** Helper classes not injected into the app classloader.

**Fix:** Ensure `getAdditionalHelperClassNames()` in `ConfigDrivenInstrumentationModule` lists all classes used by the advice.

### Interface Instrumentation Not Working

**Symptom:** Configuring an interface doesn't instrument implementations.

**Checks:**
- The `className` must be the fully-qualified **interface** name
- The `DynamicTypeInstrumentation` uses `hasSuperType(named(...))` which matches all implementations
- Verify the implementing class is actually loaded by the JVM

### Useful Log Patterns

```bash
# Check agent startup
grep "DynamicInstrumentation" /path/to/logs/server.log

# Check what was configured
grep "Dynamic instrumentation:" /path/to/logs/server.log
grep "Class:" /path/to/logs/server.log
grep "Package:" /path/to/logs/server.log
```

---

## Full Examples

### Example 1: Spring Boot Microservice

Instrument all `@Service` and `@Repository` classes, plus extract order details with chained calls and return values:

```json
{
  "packages": [
    {
      "packageName": "com.myapp",
      "recursive": true,
      "annotations": [
        "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository"
      ]
    }
  ],
  "instrumentations": [
    {
      "className": "com.myapp.service.OrderService",
      "methodName": "createOrder",
      "attributes": [
        { "argIndex": 0, "methodCall": "getCustomerId", "attributeName": "app.customer_id" },
        { "argIndex": 0, "methodCall": "getCustomer.getAddress.getCity", "attributeName": "app.customer_city" },
        { "argIndex": 0, "methodCall": "getPaymentMethod", "attributeName": "app.payment_method" },
        { "argIndex": 0, "methodCall": "getTotalAmount", "attributeName": "app.total_amount" }
      ],
      "returnValueAttributes": [
        { "methodCall": "getId", "attributeName": "app.order_id" },
        { "methodCall": "getOrderNumber", "attributeName": "app.order_number" },
        { "methodCall": "getStatus.name", "attributeName": "app.order_status" }
      ]
    },
    {
      "className": "com.myapp.repository.OrderRepository",
      "methodName": "save",
      "attributes": [
        { "argIndex": 0, "methodCall": "getId", "attributeName": "app.order_id" }
      ],
      "returnValueAttributes": [
        { "methodCall": "getId", "attributeName": "app.saved_order_id" }
      ]
    }
  ]
}
```

### Example 2: EJB Application with Interfaces

Instrument via interfaces so all implementations are covered:

```json
{
  "instrumentations": [
    {
      "className": "com.myapp.service.IOrderService",
      "methodName": "createOrder",
      "attributes": [
        { "argIndex": 0, "methodCall": "getCustomerId", "attributeName": "app.customer_id" }
      ]
    },
    {
      "className": "com.myapp.service.ICustomerService",
      "methodName": "getCustomer",
      "attributes": [
        { "argIndex": 0, "attributeName": "app.customer_id" }
      ]
    },
    {
      "className": "com.myapp.service.IInventoryService",
      "methodName": "checkAvailability",
      "attributes": [
        { "argIndex": 0, "methodCall": "size", "attributeName": "app.item_count" }
      ]
    }
  ]
}
```

### Example 3: Broad Package Coverage (No Filtering)

Instrument every class and method in a package:

```json
{
  "packages": [
    {
      "packageName": "com.myapp.service",
      "recursive": false
    }
  ]
}
```

### Expected Trace Output

For a `POST /api/orders` call through a typical layered application:

```
POST /api/orders (HTTP span from OTel auto-instrumentation)
  └── OrderController.createOrder
      ├── code.namespace = com.myapp.controller.OrderController
      ├── code.function = createOrder
      │
      └── OrderService.createOrder
          ├── code.namespace = com.myapp.service.OrderService
          ├── code.function = createOrder
          ├── code.instrumented.interface = com.myapp.service.IOrderService
          ├── app.customer_id = 42
          ├── app.customer_city = São Paulo
          ├── app.payment_method = CREDIT_CARD
          ├── app.total_amount = 199.99
          │
          │  (return value attributes added on method exit)
          ├── app.order_id = 1001
          ├── app.order_number = ORD-2024-001
          ├── app.order_status = CONFIRMED
          │
          ├── OrderValidator.validateOrder
          │   └── code.instrumented.interface = com.myapp.service.IOrderService
          │
          ├── InventoryService.checkAvailability
          │   └── app.item_count = 3
          │
          └── OrderRepository.save
              ├── app.order_id = 1001
              └── app.saved_order_id = 1001
```

---

## Sample Projects

This repository includes three sample Spring Boot projects that demonstrate the instrumentation capabilities:

| Project | Port | Description |
|---------|------|-------------|
| `sample-spring-webmvc` | 8080 | Traditional Spring WebMVC with JPA, Async methods, and Virtual Threads |
| `sample-spring-webflux` | 8081 | Reactive Spring WebFlux with R2DBC and Reactor |
| `sample-spring-batch` | 8082 | Spring Batch with chunk-oriented processing |

### Quick Start with Sample Projects

```bash
# Build the extension
./scripts/build.sh

# Start the observability stack (Jaeger)
./scripts/start-stack.sh

# Start a sample project (WebMVC example)
cd sample-projects/sample-spring-webmvc
./scripts/run.sh

# Test the instrumentation
./scripts/test.sh
```

### View Traces

Open Jaeger at http://localhost:16686 and search for the sample service.

For detailed instructions, see [USING-SAMPLE-PROJECTS.md](USING-SAMPLE-PROJECTS.md).

---

## Further Reading

- [QUICKSTART.md](QUICKSTART.md) — Quick start guide with common commands
- [ARCHITECTURE.md](ARCHITECTURE.md) — Detailed architecture and instrumentation flow diagrams
- [ROADMAP.md](ROADMAP.md) — Planned features and enhancements
- [USING-SAMPLE-PROJECTS.md](USING-SAMPLE-PROJECTS.md) — Guide to using the sample projects
- [OpenTelemetry Java Agent Extensions](https://opentelemetry.io/docs/zero-code/java/agent/extensions/)
- [ByteBuddy Documentation](https://bytebuddy.net/)
- [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)

---

**Version:** 1.0.0 | **Java:** 8+ | **OTel Agent:** 2.25.0+