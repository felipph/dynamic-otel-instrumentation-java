# Dynamic OpenTelemetry Instrumentation Extension

A **configuration-driven** extension for the [OpenTelemetry Java Agent](https://github.com/open-telemetry/opentelemetry-java-instrumentation) that lets you instrument any Java application — without modifying source code — by editing a single JSON file.

---

## Table of Contents

1. [Overview](#overview)
2. [Quick Start](#quick-start)
3. [Configuration Reference](#configuration-reference)
4. [Instrumentation Modes](#instrumentation-modes)
5. [Span Attributes](#span-attributes)
6. [Deployment with Docker](#deployment-with-docker)
7. [JMX Management](#jmx-management)
8. [Troubleshooting](#troubleshooting)
9. [Full Examples](#full-examples)
10. [Further Reading](#further-reading)

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
| **ClassLoader Safe** | Runs as an OTel Agent extension — classloader isolation handled automatically |
| **JMX Management** | Runtime control via JMX MBeans |

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

#### `AttributeDefinition`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `argIndex` | int | **Yes** | 0-based index of the method argument |
| `methodCall` | String | No | Method to invoke on the argument (e.g., `getId`). If omitted or `"toString"`, uses `arg.toString()` |
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

## Span Attributes

Every instrumented method produces a span with these **automatic attributes**:

| Attribute | Description | Example |
|-----------|-------------|---------|
| `code.namespace` | Fully qualified class name | `com.myapp.service.OrderService` |
| `code.function` | Method name | `createOrder` |
| `code.instrumented.interface` | *(Only if matched via interface)* The interface name | `com.myapp.service.IOrderService` |

Plus any **custom attributes** defined in the `attributes` array of the method-level config.

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

1. **Reloads** the JSON configuration from disk
2. **Updates** the internal registry with new instrumentation rules
3. **Retransforms** all loaded classes that match the new configuration (including via interface hierarchy)

This allows you to add, remove, or modify instrumentation rules at runtime without restarting the application.

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

Instrument all `@Service` and `@Repository` classes, plus extract order details:

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
        { "argIndex": 0, "methodCall": "getPaymentMethod", "attributeName": "app.payment_method" },
        { "argIndex": 0, "methodCall": "getTotalAmount", "attributeName": "app.total_amount" }
      ]
    },
    {
      "className": "com.myapp.repository.OrderRepository",
      "methodName": "save",
      "attributes": [
        { "argIndex": 0, "methodCall": "getId", "attributeName": "app.order_id" }
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
          ├── app.payment_method = CREDIT_CARD
          │
          ├── OrderValidator.validateOrder
          │   └── code.instrumented.interface = com.myapp.service.IOrderService
          │
          ├── InventoryService.checkAvailability
          │   └── app.item_count = 3
          │
          └── OrderRepository.save
              └── app.order_id = 1001
```

---

## Further Reading

- [QUICKSTART.md](QUICKSTART.md) — Quick start guide with common commands
- [ARCHITECTURE.md](ARCHITECTURE.md) — Detailed architecture and instrumentation flow diagrams
- [ROADMAP.md](ROADMAP.md) — Planned features and enhancements
- [OpenTelemetry Java Agent Extensions](https://opentelemetry.io/docs/zero-code/java/agent/extensions/)
- [ByteBuddy Documentation](https://bytebuddy.net/)
- [OpenTelemetry Java Instrumentation](https://github.com/open-telemetry/opentelemetry-java-instrumentation)

---

**Version:** 1.0.0 | **Java:** 8+ | **OTel Agent:** 2.25.0+