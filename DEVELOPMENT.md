# Development Guide

A comprehensive guide for developers who want to understand, modify, or extend the Dynamic OpenTelemetry Instrumentation Extension.

---

## Table of Contents

1. [Project Structure](#project-structure)
2. [Technology Stack](#technology-stack)
3. [Key Concepts](#key-concepts)
4. [Build & Run](#build--run)
5. [Code Walkthrough](#code-walkthrough)
6. [Adding New Features](#adding-new-features)
7. [Common Pitfalls](#common-pitfalls)
8. [Testing](#testing)
9. [Docker Development Workflow](#docker-development-workflow)

---

## Project Structure

```
java-otel-instrumentation/
│
├── pom.xml                                          # Maven POM (Java 8, shaded JAR)
│
├── src/main/java/com/otel/dynamic/
│   │
│   ├── extension/                                   # OTel Agent Extension (core)
│   │   ├── ConfigDrivenInstrumentationModule.java   # SPI entry point — reads config, creates instrumentations
│   │   ├── DynamicTypeInstrumentation.java          # Per-class type matcher (method-level)
│   │   ├── PackageTypeInstrumentation.java          # Per-package type matcher (package-level)
│   │   ├── DynamicAdvice.java                       # ByteBuddy advice — creates spans
│   │   └── DynamicInstrumentationConfig.java        # Cross-classloader rule registry (System properties)
│   │
│   ├── config/                                      # Configuration loading (agent classloader)
│   │   ├── ConfigurationManager.java                # Singleton — loads & caches instrumentation.json
│   │   ├── ConfigurationWatcher.java                # File watcher with debouncing
│   │   └── model/
│   │       ├── InstrumentationConfig.java           # Root config: { packages, instrumentations }
│   │       ├── MethodConfig.java                    # { className, methodName, attributes }
│   │       ├── PackageConfig.java                   # { packageName, recursive, annotations }
│   │       └── AttributeDefinition.java             # { argIndex, methodCall, attributeName }
│   │
│   ├── jmx/                                         # JMX Management
│   │   ├── ConfigManagerMBean.java                  # MBean interface
│   │   └── ConfigManager.java                       # MBean implementation
│   │
│   └── util/
│       └── Logger.java                              # Simple logging utility
│
├── src/test/java/                                   # Unit tests
│
├── docker/                                          # Docker deployment
│   ├── docker-compose.yml                           # Full stack: Jaeger + JBoss
│   ├── configs/
│   │   ├── instrumentation.json                     # Sample instrumentation config
│   │   └── standalone.conf                          # WildFly JVM args (-javaagent)
│   └── otel-collector-config.yaml                   # OTel Collector pipeline config
│
├── scripts/
│   ├── build.sh                                     # Build the extension JAR
│   ├── reload.sh                                    # Trigger JMX config reload
│   ├── start-stack.sh                               # Start Docker stack
│   └── stop-stack.sh                                # Stop Docker stack
│
├── README.md                                        # User documentation
├── DEVELOPMENT.md                                   # This file
└── ARCHITECTURE.md                                  # Architecture & flow diagrams
```

---

## Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 8+ (source/target 1.8) | Language |
| **Maven** | 3.6+ | Build tool |
| **OpenTelemetry Java Agent** | 2.25.0-alpha | Host agent that loads this extension |
| **OpenTelemetry API** | 1.48.0 | Span creation, tracer access |
| **ByteBuddy** | 1.15.10 | Bytecode manipulation (advice injection) |
| **Jackson** | 2.18.2 | JSON config parsing (shaded) |
| **Google AutoService** | 1.1.1 | SPI registration via annotation |
| **JUnit 4** | 4.13.2 | Unit testing |
| **Mockito** | 5.14.2 | Mocking |

### Dependency Scopes

- **`provided`**: OTel API, OTel Extension API, ByteBuddy — supplied by the OTel Java Agent at runtime
- **`compile` (shaded)**: Jackson — bundled into the extension JAR with relocated packages
- **`provided` (compile-time only)**: AutoService — generates `META-INF/services` files during compilation

### Shading

The Maven Shade Plugin relocates Jackson to avoid conflicts with the target application:

```
com.fasterxml.jackson.* → com.otel.dynamic.shaded.jackson.*
```

The `ServicesResourceTransformer` merges SPI service files so AutoService-generated entries survive shading.

---

## Key Concepts

### 1. OTel Java Agent Extension Model

This project is **not** a standalone Java agent. It is an **extension** that plugs into the official OpenTelemetry Java Agent via the [Extension API](https://opentelemetry.io/docs/zero-code/java/agent/extensions/).

The OTel agent:
- Handles `-javaagent` bootstrap
- Manages classloader isolation
- Provides the `InstrumentationModule` SPI
- Injects helper classes into application classloaders
- Applies ByteBuddy transformations

Our extension:
- Implements `InstrumentationModule` (discovered via SPI)
- Reads `instrumentation.json` at startup
- Returns `TypeInstrumentation` instances that define what to match and how to transform

### 2. Classloader Isolation

This is the **most critical concept** to understand.

```
┌─────────────────────────────────────┐
│  Agent Classloader                  │
│  (OTel Agent + this extension)      │
│                                     │
│  ✓ ConfigurationManager             │
│  ✓ Jackson (shaded)                 │
│  ✓ ConfigDrivenInstrumentationModule│
│  ✓ All model classes                │
└──────────────┬──────────────────────┘
               │ Helper class injection
               ▼
┌─────────────────────────────────────┐
│  Application Classloader            │
│  (Your app: JBoss, Spring, etc.)    │
│                                     │
│  ✓ DynamicAdvice        (injected)  │
│  ✓ DynamicInstrumentationConfig     │
│  ✓ DynamicInstrumentationConfig     │
│      $AttributeRule     (injected)  │
│  ✓ DynamicInstrumentationConfig     │
│      $RuleMatch         (injected)  │
│                                     │
│  ✗ ConfigurationManager  (NOT here) │
│  ✗ Jackson               (NOT here) │
└─────────────────────────────────────┘
```

**Rules:**
- `DynamicAdvice` code runs **inlined** in the application classloader
- It **cannot** reference `ConfigurationManager`, Jackson, or any agent-only class
- Data must be passed via a classloader-neutral mechanism → **System properties**
- `DynamicInstrumentationConfig` uses `System.getProperty()` / `System.setProperty()` to share data across classloaders

### 3. System Properties as Cross-Classloader Registry

`DynamicInstrumentationConfig` stores attribute extraction rules in System properties:

```
Key:   otel.dynamic.rules.com.myapp.service.OrderService#createOrder
Value: 0|getCustomerId|app.customer_id;0|getPaymentMethod|app.payment_method
```

Format: `argIndex|methodCall|attributeName` separated by `;`

This is populated by `ConfigDrivenInstrumentationModule` (agent classloader) and read by `DynamicAdvice` (app classloader).

### 4. ByteBuddy Advice Inlining

ByteBuddy `@Advice` methods are **inlined** into the target method's bytecode. They are NOT called as separate methods. This means:

- Static fields in the advice class are resolved in the **target class's classloader**
- You cannot use lambdas or method references in advice code
- You cannot catch exceptions from advice code normally
- `@Advice.Origin("#t")` returns the internal class name with slashes (`com/sample/app/Foo`), not dots

### 5. Type Matching

Two matching strategies:

| Strategy | Matcher | Used By |
|----------|---------|---------|
| **Class/Interface** | `hasSuperType(named("com.myapp.IService"))` | `DynamicTypeInstrumentation` |
| **Package** | `nameStartsWith("com.myapp.")` + optional annotation filter | `PackageTypeInstrumentation` |

`hasSuperType` matches the named type **and** all classes that extend/implement it. This is how interface-level instrumentation works.

### 6. Hierarchy-Aware Rule Lookup

When `DynamicAdvice` fires on a method, it needs to find the attribute extraction rules. The rules might be registered under an interface name, but the advice fires on the concrete class.

`DynamicInstrumentationConfig.findRulesForHierarchy()` walks:
1. Exact class name (fast path)
2. All interfaces of the class
3. Superclass chain + their interfaces

Returns a `RuleMatch` with the source class name and whether it came from an interface.

---

## Build & Run

### Build

```bash
# Full build
mvn clean package -DskipTests

# Or use the script
bash scripts/build.sh
```

Output: `target/dynamic-instrumentation-agent-1.1.0.jar`

### Run Tests

```bash
mvn test
```

### Run with Docker Stack

```bash
# Build the extension
bash scripts/build.sh

# Start the full stack (Jaeger + JBoss + PostgreSQL)
cd docker
docker compose up -d

# Watch JBoss logs
docker compose logs -f jboss

# Restart JBoss after rebuilding
docker compose restart jboss

# Stop everything
docker compose down
```

### Verify It Works

1. Build and start the stack
2. Wait for JBoss to finish deploying (~60-90s)
3. Look for `[DynamicInstrumentation] [INFO] Dynamic instrumentation: N classes configured` in logs
4. Make HTTP requests to the application
5. Open Jaeger at `http://localhost:16686` and search for your service

---

## Code Walkthrough

### Startup Flow

```
1. JVM starts with -javaagent:opentelemetry-javaagent.jar
2. OTel Agent initializes
3. Agent scans for InstrumentationModule implementations via SPI
4. Finds ConfigDrivenInstrumentationModule (registered by @AutoService)
5. Calls typeInstrumentations()
   ├── Reads instrumentation.config.path system property
   ├── Initializes ConfigurationManager → parses instrumentation.json
   ├── Populates DynamicInstrumentationConfig registry (System properties)
   ├── Creates DynamicTypeInstrumentation per class (method-level)
   └── Creates PackageTypeInstrumentation per package (package-level)
6. Agent calls isHelperClass() and getAdditionalHelperClassNames()
   └── Returns DynamicAdvice, DynamicInstrumentationConfig, inner classes
7. Agent applies ByteBuddy transformations as classes are loaded
8. When a matched class loads, advice is inlined into matched methods
```

### Request-Time Flow

```
1. Application method is called (e.g., OrderService.createOrder)
2. DynamicAdvice.onEnter() fires (inlined bytecode)
   ├── Converts class name from slashes to dots
   ├── Gets Tracer from GlobalOpenTelemetry
   ├── Creates and starts a Span
   ├── Calls findRulesForHierarchy() to find attribute rules
   │   ├── Checks exact class → System property lookup
   │   ├── If not found, walks interfaces → System property lookup
   │   └── If not found, walks superclasses → System property lookup
   ├── If match came from interface → sets code.instrumented.interface attribute
   ├── If no rules (package-level) → checks if method declared by an interface
   ├── Extracts custom attributes via reflection on method arguments
   └── Returns Scope (span made current)
3. Original method executes
4. DynamicAdvice.onExit() fires
   ├── Closes Scope
   ├── If exception → records it on the span, sets ERROR status
   └── Ends the Span
5. Span is exported via OTLP by the OTel Agent
```

### Key Classes in Detail

#### `ConfigDrivenInstrumentationModule`

The SPI entry point. Responsibilities:
- Read config path from `instrumentation.config.path` system property
- Initialize `ConfigurationManager` to parse JSON
- Convert `MethodConfig` → `DynamicInstrumentationConfig` registry entries (System properties)
- Create `DynamicTypeInstrumentation` for each class
- Create `PackageTypeInstrumentation` for each package
- Declare helper classes for injection into app classloader

#### `DynamicTypeInstrumentation`

Matches a single class (or interface + all implementations) and instruments specific methods.

- `typeMatcher()`: `hasSuperType(named(className))` — matches the class and all subclasses/implementations
- `transform()`: applies `DynamicAdvice` to each configured method name

#### `PackageTypeInstrumentation`

Matches all classes in a package, optionally filtered by annotations.

- `typeMatcher()`: combines package prefix matching with optional annotation filtering
- Non-recursive: matches only direct members of the package (no dots in remainder after prefix)
- Recursive: matches everything under the package prefix
- Annotation filter: `isAnnotatedWith(named(annotation))` combined with OR logic
- `transform()`: applies `DynamicAdvice` to all declared, non-synthetic, non-constructor methods

#### `DynamicAdvice`

The ByteBuddy advice that gets inlined into instrumented methods.

- `onEnter()`: creates span, detects interface origin, extracts custom attributes
- `onExit()`: records exceptions, ends span
- Uses `@Advice.Origin("#t")` and `@Advice.Origin("#m")` for efficient class/method name access
- Uses `@Advice.Local("otelSpan")` to pass span from enter to exit

#### `DynamicInstrumentationConfig`

Cross-classloader registry using System properties.

- `register()`: serializes rules to a System property (called from agent classloader)
- `getRules()`: deserializes rules from a System property (called from app classloader)
- `findRulesForHierarchy()`: walks class hierarchy to find rules registered under interfaces/superclasses
- `RuleMatch`: carries rules + source class name + `fromInterface` flag
- `clear()`: removes all registered rules (for hot-reload)

---

## Adding New Features

### Adding a New Span Attribute (Automatic)

To add a new attribute that is set on every span:

1. Edit `DynamicAdvice.onEnter()`
2. Add `span.setAttribute("my.attribute", value)` after the span is created
3. Rebuild and test

### Adding a New Config Field

To add a new field to the JSON configuration:

1. **Add the field to the model class** (e.g., `MethodConfig.java` or `PackageConfig.java`)
   - Add the field with `@JsonProperty` annotation
   - Add getter/setter
   - Update constructors and `toString()`

2. **Use the field in the instrumentation module** (`ConfigDrivenInstrumentationModule`)
   - Access it when creating `TypeInstrumentation` instances

3. **If the field affects advice behavior**, serialize it into a System property
   - Update `DynamicInstrumentationConfig.register()` and `getRules()`
   - Update `DynamicAdvice` to read and use the new data

### Adding a New TypeInstrumentation

To add a completely new matching strategy:

1. Create a new class implementing `TypeInstrumentation`
2. Implement `typeMatcher()` — returns a ByteBuddy `ElementMatcher<TypeDescription>`
3. Implement `transform()` — applies advice to matched methods
4. Register it in `ConfigDrivenInstrumentationModule.typeInstrumentations()`

### Adding a New Helper Class

If your new code needs to run in the app classloader (referenced by advice):

1. Add the fully-qualified class name to `getAdditionalHelperClassNames()` in `ConfigDrivenInstrumentationModule`
2. For inner classes, use the `$` notation: `com.otel.dynamic.extension.MyClass$InnerClass`

---

## Common Pitfalls

### 1. Referencing Agent-Only Classes in Advice

**Wrong:**
```java
// In DynamicAdvice (runs in app classloader)
ConfigurationManager.getInstance().getConfig(); // ClassNotFoundException!
```

**Right:**
```java
// Use DynamicInstrumentationConfig (injected as helper)
DynamicInstrumentationConfig.getRules(className, methodName);
```

### 2. Forgetting to Register Helper Classes

If you add a new class or inner class that is referenced by `DynamicAdvice`, you **must** add it to `getAdditionalHelperClassNames()`. Otherwise you'll get `ClassNotFoundException` at runtime.

### 3. Using Lambdas in Advice

ByteBuddy advice inlining does not support lambdas or method references. Use anonymous inner classes or plain loops instead.

### 4. Slash vs Dot Class Names

`@Advice.Origin("#t")` returns `com/sample/app/Foo` (slashes). Always convert:
```java
String dotClassName = className.replace('/', '.');
```

### 5. Modifying Advice Without Rebuilding

Advice code is inlined at class-load time. Changing `DynamicAdvice.java` requires:
1. Rebuild the extension JAR
2. Restart the application (or the container)

### 6. Jackson in the App Classloader

Jackson is shaded and only available in the agent classloader. Never reference Jackson classes in `DynamicAdvice` or `DynamicInstrumentationConfig`.

### 7. System Property Limits

System properties are stored as strings. Very large configurations (thousands of rules) may have performance implications due to string serialization/deserialization on every method call.

---

## Testing

### Unit Tests

```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=ConfigurationManagerTest

# Run with verbose output
mvn test -Dsurefire.useFile=false
```

### Integration Testing (Manual)

1. **Build**: `bash scripts/build.sh`
2. **Start stack**: `cd docker && docker compose up -d`
3. **Wait for startup**: `docker compose logs -f jboss` (wait for deployment complete)
4. **Generate traffic**: Make HTTP requests to the application
5. **Verify traces**: Open Jaeger at `http://localhost:16686`
6. **Check attributes**: Click on a span and verify `code.namespace`, `code.function`, `code.instrumented.interface`, and custom attributes

### Verifying Specific Features

| Feature | How to Verify |
|---------|---------------|
| Method-level instrumentation | Check span exists with correct `code.namespace` and `code.function` |
| Custom attribute extraction | Check span has the configured custom attributes with correct values |
| Interface instrumentation | Configure an interface name → verify implementations produce spans |
| `code.instrumented.interface` | Check the attribute appears on spans matched via interface |
| Package-level instrumentation | Configure a package → verify all classes in it produce spans |
| Annotation filtering | Configure annotations → verify only annotated classes produce spans |

---

## Docker Development Workflow

### Quick Iteration Cycle

```bash
# 1. Make code changes

# 2. Rebuild
bash scripts/build.sh

# 3. Restart JBoss (picks up new JAR via volume mount)
cd docker && docker compose restart jboss

# 4. Check logs
docker compose logs jboss --tail 30

# 5. Test and verify in Jaeger (http://localhost:16686)
```

### Modifying instrumentation.json

The config file is mounted as a read-only volume. To change it:

1. Edit `docker/configs/instrumentation.json` (or the file your compose mounts)
2. Restart JBoss: `docker compose restart jboss`

### Useful Docker Commands

```bash
# View real-time logs
docker compose logs -f jboss

# Check container status
docker compose ps

# Enter JBoss container
docker compose exec jboss bash

# Check if the extension JAR is mounted correctly
docker compose exec jboss ls -la /opt/jboss/agents/

# Check if the config file is mounted
docker compose exec jboss cat /opt/otel/config/instrumentation.json

# Full restart (if things are broken)
docker compose down && docker compose up -d
```

### Ports

| Port | Service | Description |
|------|---------|-------------|
| 8080 | JBoss | Application HTTP |
| 16686 | Jaeger | Tracing UI |
| 4317 | OTel Collector | OTLP gRPC |
| 4318 | OTel Collector | OTLP HTTP |
| 9990 | JBoss | Management Console |
| 8787 | JBoss | Remote Debug |
