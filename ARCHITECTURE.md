# Architecture & Instrumentation Flow

Detailed diagrams showing how the Dynamic OpenTelemetry Instrumentation Extension loads, applies, and executes instrumentation at every stage.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Component Diagram](#component-diagram)
3. [Classloader Architecture](#classloader-architecture)
4. [Startup & Instrumentation Loading Flow](#startup--instrumentation-loading-flow)
5. [Type Matching Flow](#type-matching-flow)
6. [Request-Time Span Creation Flow](#request-time-span-creation-flow)
7. [Interface Detection Flow](#interface-detection-flow)
8. [Attribute Extraction Flow](#attribute-extraction-flow)
9. [Cross-Classloader Data Flow](#cross-classloader-data-flow)
10. [Docker Deployment Topology](#docker-deployment-topology)
11. [Configuration Model](#configuration-model)

---

## System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              JVM Process                                    │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    OpenTelemetry Java Agent                           │  │
│  │                    (opentelemetry-javaagent.jar)                      │  │
│  │                                                                       │  │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │  │
│  │  │          Dynamic Instrumentation Extension                      │  │  │
│  │  │          (dynamic-instrumentation-agent-1.0.0.jar)              │  │  │
│  │  │                                                                 │  │  │
│  │  │  ConfigDrivenInstrumentationModule ◄── SPI Discovery            │  │  │
│  │  │       │                                                         │  │  │
│  │  │       ├── ConfigurationManager ── instrumentation.json          │  │  │
│  │  │       │                                                         │  │  │
│  │  │       ├── DynamicTypeInstrumentation (per class)                │  │  │
│  │  │       │                                                         │  │  │
│  │  │       └── PackageTypeInstrumentation (per package)              │  │  │
│  │  └─────────────────────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    Application (JBoss / Tomcat / Spring Boot)         │  │
│  │                                                                       │  │
│  │  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐   │  │
│  │  │ Controller   │  │ Service      │  │ Repository               │   │  │
│  │  │ (instrumented│  │ (instrumented│  │ (instrumented)           │   │  │
│  │  │  by advice)  │  │  by advice)  │  │                          │   │  │
│  │  └──────────────┘  └──────────────┘  └──────────────────────────┘   │  │
│  │                                                                       │  │
│  │  Injected helpers: DynamicAdvice, DynamicInstrumentationConfig        │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                             │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │                    System Properties (shared)                         │  │
│  │                                                                       │  │
│  │  otel.dynamic.rules.com.myapp.IOrderService#createOrder =            │  │
│  │      0|getCustomerId|app.customer_id;0|getPaymentMethod|app.method   │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ OTLP gRPC (:4317)
                                       ▼
                          ┌────────────────────────┐
                          │   Jaeger All-in-One    │
                          │   (Collector + Query   │
                          │    + UI)               │
                          │   :4317 OTLP gRPC      │
                          │   :4318 OTLP HTTP      │
                          │   :16686 Tracing UI    │
                          └────────────────────────┘
```

---

## Component Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│                        Extension Components                          │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              ConfigDrivenInstrumentationModule                │   │
│  │              (SPI Entry Point)                                │   │
│  │                                                              │   │
│  │  Responsibilities:                                           │   │
│  │  • Discovered by OTel Agent via @AutoService                 │   │
│  │  • Reads instrumentation.config.path system property         │   │
│  │  • Initializes ConfigurationManager                          │   │
│  │  • Populates DynamicInstrumentationConfig registry           │   │
│  │  • Creates TypeInstrumentation instances                     │   │
│  │  • Declares helper classes for app classloader injection     │   │
│  └──────────┬──────────────────────────┬────────────────────────┘   │
│             │                          │                             │
│             ▼                          ▼                             │
│  ┌─────────────────────┐  ┌──────────────────────────┐             │
│  │ DynamicType         │  │ PackageType              │             │
│  │ Instrumentation     │  │ Instrumentation          │             │
│  │                     │  │                          │             │
│  │ • One per class     │  │ • One per package        │             │
│  │ • hasSuperType()    │  │ • nameStartsWith()       │             │
│  │   matcher           │  │   matcher                │             │
│  │ • Specific methods  │  │ • Optional annotation    │             │
│  │                     │  │   filter                 │             │
│  │ • Supports          │  │ • All declared methods   │             │
│  │   interfaces        │  │   (non-synthetic,        │             │
│  │                     │  │    non-constructor)      │             │
│  └─────────┬───────────┘  └────────────┬─────────────┘             │
│            │                           │                            │
│            └─────────┬─────────────────┘                            │
│                      ▼                                              │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      DynamicAdvice                            │   │
│  │                      (Injected into app classloader)          │   │
│  │                                                              │   │
│  │  @OnMethodEnter:                                             │   │
│  │  • Create Span (via GlobalOpenTelemetry)                     │   │
│  │  • Detect interface origin                                   │   │
│  │  • Extract custom attributes                                 │   │
│  │  • Make span current                                         │   │
│  │                                                              │   │
│  │  @OnMethodExit:                                              │   │
│  │  • Close scope                                               │   │
│  │  • Record exception (if any)                                 │   │
│  │  • End span                                                  │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              DynamicInstrumentationConfig                     │   │
│  │              (Injected into app classloader)                  │   │
│  │                                                              │   │
│  │  • register()            → writes System property            │   │
│  │  • getRules()            → reads System property             │   │
│  │  • findRulesForHierarchy → walks class hierarchy             │   │
│  │  • clear()               → removes all rules                 │   │
│  │                                                              │   │
│  │  Inner classes:                                              │   │
│  │  • AttributeRule  (argIndex, methodCall, attributeName)      │   │
│  │  • RuleMatch      (sourceClassName, rules, fromInterface)    │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │              Configuration Layer                              │   │
│  │              (Agent classloader only)                         │   │
│  │                                                              │   │
│  │  ConfigurationManager ──► InstrumentationConfig              │   │
│  │       │                        ├── List<MethodConfig>        │   │
│  │       │                        └── List<PackageConfig>       │   │
│  │       │                                                      │   │
│  │       ├── ConfigurationWatcher (file change detection)       │   │
│  │       └── Jackson ObjectMapper (shaded)                      │   │
│  │                                                              │   │
│  │  JMX: ConfigManager / ConfigManagerMBean                     │   │
│  └──────────────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Classloader Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Bootstrap Classloader                        │
│                        (JVM core classes)                            │
└──────────────────────────────┬──────────────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              │                                 │
              ▼                                 ▼
┌──────────────────────────┐    ┌──────────────────────────────────┐
│  Agent Classloader       │    │  Application Classloader         │
│                          │    │  (JBoss Module / Tomcat / etc.)  │
│  Loaded from:            │    │                                  │
│  • opentelemetry-        │    │  Loaded from:                    │
│    javaagent.jar         │    │  • Application WAR/EAR           │
│  • dynamic-              │    │  • Application libraries         │
│    instrumentation-      │    │                                  │
│    agent-1.0.0.jar       │    │  Injected by OTel Agent:         │
│                          │    │  ┌────────────────────────────┐  │
│  Contains:               │    │  │ DynamicAdvice              │  │
│  ┌────────────────────┐  │    │  │ DynamicInstrumentationConf │  │
│  │ ConfigDriven       │  │    │  │ DynamicInstrumentationConf │  │
│  │ Instrumentation    │──┼────┼─▶│   $AttributeRule           │  │
│  │ Module             │  │    │  │ DynamicInstrumentationConf │  │
│  │                    │  │    │  │   $RuleMatch               │  │
│  │ ConfigurationMgr   │  │    │  └────────────────────────────┘  │
│  │ Jackson (shaded)   │  │    │                                  │
│  │ Model classes      │  │    │  Your application classes:       │
│  │ Logger             │  │    │  ┌────────────────────────────┐  │
│  │ JMX ConfigManager  │  │    │  │ OrderService               │  │
│  └────────────────────┘  │    │  │ CustomerRepository         │  │
│                          │    │  │ PaymentProcessor           │  │
│  NOT visible to app ──── │    │  │ ... (with inlined advice)  │  │
│                          │    │  └────────────────────────────┘  │
└──────────────────────────┘    └──────────────────────────────────┘
              │                                 │
              │    System Properties            │
              │    (shared across all           │
              │     classloaders)               │
              │         │                       │
              └────────►├◄──────────────────────┘
                        │
              ┌─────────┴─────────────────────┐
              │ otel.dynamic.rules.*          │
              │ (attribute extraction rules)  │
              └───────────────────────────────┘
```

---

## Startup & Instrumentation Loading Flow

```
JVM Start
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ 1. OTel Java Agent Initializes                                  │
│    -javaagent:opentelemetry-javaagent.jar                       │
│    -Dotel.javaagent.extensions=dynamic-instrumentation-ext.jar  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 2. SPI Discovery                                                │
│    Agent scans META-INF/services/                               │
│    io.opentelemetry.javaagent.extension.instrumentation.        │
│    InstrumentationModule                                        │
│                                                                 │
│    Finds: ConfigDrivenInstrumentationModule                     │
│    (registered by @AutoService annotation at compile time)      │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 3. ConfigDrivenInstrumentationModule.typeInstrumentations()     │
│                                                                 │
│    3a. Read config path                                         │
│        configPath = System.getProperty(                         │
│            "instrumentation.config.path")                       │
│                                                                 │
│    3b. Initialize ConfigurationManager                          │
│        ┌──────────────────────────────────┐                     │
│        │ ConfigurationManager.initialize() │                    │
│        │   └── ObjectMapper.readValue()    │                    │
│        │       └── instrumentation.json    │                    │
│        │           parsed into             │                    │
│        │           InstrumentationConfig   │                    │
│        └──────────────────────────────────┘                     │
│                                                                 │
│    3c. Populate cross-classloader registry                      │
│        For each MethodConfig:                                   │
│        ┌──────────────────────────────────────────────────┐     │
│        │ DynamicInstrumentationConfig.register(            │    │
│        │   "com.myapp.IOrderService",                      │    │
│        │   "createOrder",                                  │    │
│        │   [AttributeRule(0,"getCustomerId","app.cust_id")]│    │
│        │ )                                                 │    │
│        │                                                   │    │
│        │ → System.setProperty(                             │    │
│        │     "otel.dynamic.rules.com.myapp.IOrderService   │    │
│        │      #createOrder",                               │    │
│        │     "0|getCustomerId|app.cust_id"                 │    │
│        │   )                                               │    │
│        └──────────────────────────────────────────────────┘     │
│                                                                 │
│    3d. Create TypeInstrumentation instances                     │
│        ┌──────────────────────────────────────────────────┐     │
│        │ Method-level (from "instrumentations"):           │    │
│        │   new DynamicTypeInstrumentation(                 │    │
│        │     "com.myapp.IOrderService",                    │    │
│        │     ["createOrder", "processOrder"]               │    │
│        │   )                                               │    │
│        │                                                   │    │
│        │ Package-level (from "packages"):                  │    │
│        │   new PackageTypeInstrumentation(                 │    │
│        │     "com.myapp",                                  │    │
│        │     recursive=true,                               │    │
│        │     annotations=["...Service", "...Repository"]   │    │
│        │   )                                               │    │
│        └──────────────────────────────────────────────────┘     │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 4. Helper Class Injection                                       │
│                                                                 │
│    Agent calls getAdditionalHelperClassNames():                 │
│    ┌──────────────────────────────────────────────────────┐     │
│    │ • com.otel.dynamic.extension.DynamicAdvice            │    │
│    │ • com.otel.dynamic.extension.                         │    │
│    │     DynamicInstrumentationConfig                      │    │
│    │ • com.otel.dynamic.extension.                         │    │
│    │     DynamicInstrumentationConfig$AttributeRule         │    │
│    │ • com.otel.dynamic.extension.                         │    │
│    │     DynamicInstrumentationConfig$RuleMatch             │    │
│    └──────────────────────────────────────────────────────┘     │
│                                                                 │
│    These classes are injected into the app classloader           │
│    so inlined advice code can resolve them at runtime.           │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ 5. ByteBuddy Transformation (on class load)                     │
│                                                                 │
│    When the JVM loads a class (e.g., OrderServiceImpl):         │
│                                                                 │
│    5a. Check type matchers:                                     │
│        DynamicTypeInstrumentation.typeMatcher()                 │
│          → hasSuperType(named("com.myapp.IOrderService"))       │
│          → MATCHES (OrderServiceImpl implements IOrderService)  │
│                                                                 │
│        PackageTypeInstrumentation.typeMatcher()                 │
│          → nameStartsWith("com.myapp.")                         │
│            AND isAnnotatedWith(named("...Service"))             │
│          → MATCHES (if class is in package and has annotation)  │
│                                                                 │
│    5b. Apply advice:                                            │
│        transform() → applyAdviceToMethod(                       │
│          named("createOrder"),                                  │
│          DynamicAdvice.class.getName()                          │
│        )                                                        │
│                                                                 │
│    5c. ByteBuddy inlines DynamicAdvice.onEnter() and            │
│        DynamicAdvice.onExit() into the method's bytecode        │
│                                                                 │
│    Result: OrderServiceImpl.createOrder() now has               │
│    span creation code embedded in its bytecode                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Type Matching Flow

### Method-Level Matching (DynamicTypeInstrumentation)

```
Class being loaded: com.myapp.service.OrderServiceImpl
Config entry:       className = "com.myapp.service.IOrderService"

                    typeMatcher()
                        │
                        ▼
            hasSuperType(named("com.myapp.service.IOrderService"))
                        │
                        ▼
            Does OrderServiceImpl extend or implement IOrderService?
                        │
                ┌───────┴───────┐
                │               │
               YES             NO
                │               │
                ▼               ▼
          MATCH ✓          SKIP ✗
                │
                ▼
          transform()
                │
                ▼
          For each configured method name:
          applyAdviceToMethod(named("createOrder"), DynamicAdvice)
          applyAdviceToMethod(named("processOrder"), DynamicAdvice)
```

### Package-Level Matching (PackageTypeInstrumentation)

```
Class being loaded: com.myapp.service.impl.OrderServiceImpl
Config entry:       packageName = "com.myapp.service", recursive = true

                    typeMatcher()
                        │
                        ▼
            ┌───────────────────────────────────┐
            │ Step 1: Package match             │
            │                                   │
            │ recursive=true?                   │
            │   YES → nameStartsWith(           │
            │           "com.myapp.service.")    │
            │   NO  → name starts with prefix   │
            │         AND no more dots after it  │
            │                                   │
            │ "com.myapp.service.impl.          │
            │  OrderServiceImpl"                │
            │  starts with "com.myapp.service." │
            │  → PASS ✓                         │
            └──────────────┬────────────────────┘
                           │
                           ▼
            ┌───────────────────────────────────┐
            │ Step 2: Annotation filter         │
            │ (only if annotations non-empty)   │
            │                                   │
            │ annotations = ["...Service"]      │
            │                                   │
            │ isAnnotatedWith(named(            │
            │   "org.springframework.           │
            │    stereotype.Service"))           │
            │                                   │
            │ OrderServiceImpl has @Service?     │
            │   YES → PASS ✓                    │
            │   NO  → FAIL ✗                    │
            └──────────────┬────────────────────┘
                           │
                           ▼
            ┌───────────────────────────────────┐
            │ Step 3: Method selection          │
            │                                   │
            │ transform() applies advice to:    │
            │ • All declared methods            │
            │ • Excluding constructors          │
            │ • Excluding synthetic methods     │
            │                                   │
            │ isDeclaredBy(target)              │
            │   .and(not(isConstructor()))       │
            │   .and(not(isSynthetic()))         │
            └───────────────────────────────────┘
```

---

## Request-Time Span Creation Flow

```
Application receives HTTP request
    │
    ▼
OrderServiceImpl.createOrder(orderRequest) is called
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ DynamicAdvice.onEnter() [inlined bytecode]                      │
│                                                                 │
│  1. Convert class name                                          │
│     "com/myapp/service/impl/OrderServiceImpl"                   │
│       → "com.myapp.service.impl.OrderServiceImpl"               │
│                                                                 │
│  2. Get tracer                                                  │
│     GlobalOpenTelemetry.getTracer("dynamic-instrumentation")    │
│                                                                 │
│  3. Build span name                                             │
│     "OrderServiceImpl.createOrder"                              │
│                                                                 │
│  4. Create span                                                 │
│     tracer.spanBuilder("OrderServiceImpl.createOrder")          │
│       .setSpanKind(INTERNAL)                                    │
│       .setAttribute("code.namespace",                           │
│           "com.myapp.service.impl.OrderServiceImpl")            │
│       .setAttribute("code.function", "createOrder")             │
│       .startSpan()                                              │
│                                                                 │
│  5. Interface detection (see next diagram)                      │
│     → sets code.instrumented.interface if applicable            │
│                                                                 │
│  6. Attribute extraction (see attribute flow diagram)           │
│     → sets custom attributes from method arguments              │
│                                                                 │
│  7. Make span current                                           │
│     scope = span.makeCurrent()                                  │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Original method executes                                        │
│                                                                 │
│   OrderServiceImpl.createOrder(orderRequest) {                  │
│       // business logic runs here                               │
│       // may call other instrumented methods                    │
│       // (creating child spans)                                 │
│   }                                                             │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ DynamicAdvice.onExit() [inlined bytecode]                       │
│                                                                 │
│  1. Close scope                                                 │
│     scope.close()                                               │
│                                                                 │
│  2. Handle exception (if thrown)                                │
│     if (throwable != null) {                                    │
│       span.setStatus(ERROR, throwable.getMessage())             │
│       span.recordException(throwable)                           │
│     }                                                           │
│                                                                 │
│  3. End span                                                    │
│     span.end()                                                  │
│     → Span is queued for export via OTLP                        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Interface Detection Flow

```
DynamicAdvice.onEnter() — after span is created
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ findRulesForHierarchy("com.myapp.service.impl.OrderServiceImpl",│
│                        "createOrder")                           │
│                                                                 │
│  Step 1: Try exact class name                                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ System.getProperty(                                       │  │
│  │   "otel.dynamic.rules.com.myapp.service.impl.            │  │
│  │    OrderServiceImpl#createOrder")                         │  │
│  │                                                           │  │
│  │ Result: null (no rules registered for concrete class)     │  │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Step 2: Check interfaces of OrderServiceImpl                   │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ Class.forName("...OrderServiceImpl").getInterfaces()      │  │
│  │   → [IOrderService]                                       │  │
│  │                                                           │  │
│  │ System.getProperty(                                       │  │
│  │   "otel.dynamic.rules.com.myapp.service.                 │  │
│  │    IOrderService#createOrder")                            │  │
│  │                                                           │  │
│  │ Result: "0|getCustomerId|app.customer_id;..."             │  │
│  │ → FOUND! Return RuleMatch:                                │  │
│  │     sourceClassName = "com.myapp.service.IOrderService"   │  │
│  │     fromInterface = true                                  │  │
│  │     rules = [AttributeRule(...), ...]                     │  │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  (If not found in interfaces, would continue to Step 3:         │
│   walk superclass chain and their interfaces)                   │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│ Back in DynamicAdvice.onEnter():                                │
│                                                                 │
│  ruleMatch.isFromInterface() == true                            │
│    → span.setAttribute("code.instrumented.interface",           │
│          "com.myapp.service.IOrderService")                     │
│                                                                 │
│  Extract attributes from ruleMatch.getRules()                   │
└─────────────────────────────────────────────────────────────────┘


For PACKAGE-LEVEL instrumentation (no rules registered):

┌─────────────────────────────────────────────────────────────────┐
│ findRulesForHierarchy() returns null                            │
│ (no method-level rules for this class/method)                   │
│                                                                 │
│ Fallback: Check if method is declared by an interface           │
│                                                                 │
│  Class.forName("...OrderServiceImpl").getInterfaces()           │
│    → [IOrderService]                                            │
│                                                                 │
│  IOrderService.getMethod("createOrder")                         │
│    → Found! (method exists on interface)                        │
│                                                                 │
│  span.setAttribute("code.instrumented.interface",               │
│      "com.myapp.service.IOrderService")                         │
└─────────────────────────────────────────────────────────────────┘
```

---

## Attribute Extraction Flow

```
DynamicAdvice.onEnter() — after interface detection
    │
    │  rules = [
    │    AttributeRule(argIndex=0, methodCall="getCustomerId", attributeName="app.customer_id"),
    │    AttributeRule(argIndex=0, methodCall="getPaymentMethod", attributeName="app.payment_method")
    │  ]
    │
    │  args = [orderRequest]  (method arguments)
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ For each AttributeRule:                                         │
│                                                                 │
│  Rule: argIndex=0, methodCall="getCustomerId",                  │
│        attributeName="app.customer_id"                          │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 1. Get argument at index 0                                │  │
│  │    arg = args[0]  →  orderRequest object                  │  │
│  │                                                           │  │
│  │ 2. Check methodCall                                       │  │
│  │    "getCustomerId" is not null/empty/"toString"            │  │
│  │                                                           │  │
│  │ 3. Invoke via reflection                                  │  │
│  │    Method m = orderRequest.getClass()                     │  │
│  │                  .getMethod("getCustomerId")              │  │
│  │    Object result = m.invoke(orderRequest)                 │  │
│  │    value = result.toString()  →  "42"                     │  │
│  │                                                           │  │
│  │ 4. Set span attribute                                     │  │
│  │    span.setAttribute("app.customer_id", "42")             │  │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Rule: argIndex=0, methodCall=null,                             │
│        attributeName="app.product_id"                           │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │ 1. Get argument at index 0                                │  │
│  │    arg = args[0]  →  42 (Long)                            │  │
│  │                                                           │  │
│  │ 2. Check methodCall                                       │  │
│  │    null → use arg.toString() directly                     │  │
│  │                                                           │  │
│  │ 3. Get value                                              │  │
│  │    value = arg.toString()  →  "42"                        │  │
│  │                                                           │  │
│  │ 4. Set span attribute                                     │  │
│  │    span.setAttribute("app.product_id", "42")              │  │
│  └──────────────────────────────────────────────────────────┘   │
│                                                                 │
│  Note: Any exception during extraction is silently caught       │
│  to avoid breaking the application.                             │
└─────────────────────────────────────────────────────────────────┘
```

---

## Cross-Classloader Data Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                    AGENT CLASSLOADER                             │
│                    (Startup time)                                │
│                                                                 │
│  ConfigDrivenInstrumentationModule.typeInstrumentations()        │
│       │                                                         │
│       │  For each MethodConfig in instrumentation.json:          │
│       │                                                         │
│       ▼                                                         │
│  DynamicInstrumentationConfig.register(                         │
│    className, methodName, rules                                 │
│  )                                                              │
│       │                                                         │
│       │  Serializes rules to string:                            │
│       │  "0|getCustomerId|app.customer_id;                      │
│       │   0|getPaymentMethod|app.payment_method"                │
│       │                                                         │
│       ▼                                                         │
│  System.setProperty(                                            │
│    "otel.dynamic.rules.com.myapp.IOrderService#createOrder",    │
│    "0|getCustomerId|app.customer_id;0|getPaymentMethod|..."     │
│  )                                                              │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                    System Properties
                    (JVM-wide, shared)
                               │
┌──────────────────────────────┴──────────────────────────────────┐
│                    APPLICATION CLASSLOADER                       │
│                    (Request time)                                │
│                                                                 │
│  DynamicAdvice.onEnter() [inlined in OrderServiceImpl]          │
│       │                                                         │
│       ▼                                                         │
│  DynamicInstrumentationConfig.findRulesForHierarchy(            │
│    "com.myapp.service.impl.OrderServiceImpl", "createOrder"     │
│  )                                                              │
│       │                                                         │
│       │  Tries exact class → null                               │
│       │  Tries interface IOrderService → found!                 │
│       │                                                         │
│       ▼                                                         │
│  System.getProperty(                                            │
│    "otel.dynamic.rules.com.myapp.IOrderService#createOrder"     │
│  )                                                              │
│       │                                                         │
│       │  Returns: "0|getCustomerId|app.customer_id;..."         │
│       │                                                         │
│       ▼                                                         │
│  Deserializes to List<AttributeRule>                            │
│  Returns RuleMatch(sourceClassName, rules, fromInterface=true)  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Docker Deployment Topology

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        Docker Network: otel-network                     │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  otel-jboss (WildFly 26.1.3)                          :8080     │   │
│  │                                                                  │   │
│  │  Volumes:                                                        │   │
│  │  ├── opentelemetry-javaagent.jar                                 │   │
│  │  │     → /opt/jboss/agents/opentelemetry-javaagent.jar           │   │
│  │  ├── dynamic-instrumentation-agent-1.0.0.jar                     │   │
│  │  │     → /opt/jboss/agents/dynamic-instrumentation-extension.jar │   │
│  │  ├── instrumentation.json                                        │   │
│  │  │     → /opt/otel/config/instrumentation.json                   │   │
│  │  ├── standalone.conf                                             │   │
│  │  │     → /opt/jboss/wildfly/bin/standalone.conf                  │   │
│  │  └── sample-app.ear                                              │   │
│  │        → /opt/jboss/wildfly/standalone/deployments/              │   │
│  │                                                                  │   │
│  │  JVM Args (from standalone.conf):                                │   │
│  │    -javaagent:/opt/jboss/agents/opentelemetry-javaagent.jar      │   │
│  │    -Dotel.javaagent.extensions=...extension.jar                  │   │
│  │                                                                  │   │
│  │  JVM Args (from JAVA_OPTS_APPEND):                               │   │
│  │    -Dotel.service.name=sample-spring-mvc-app                     │   │
│  │    -Dinstrumentation.config.path=/opt/otel/config/...json        │   │
│  └──────────────────────────────┬───────────────────────────────────┘   │
│                                 │ OTLP gRPC (:4317)                     │
│                                 ▼                                       │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  jaeger (Jaeger All-in-One)              :4317 :4318 :16686     │   │
│  │  (Collector + Query + UI)                                        │   │
│  │                                                                  │   │
│  │  OTLP receiver (native) → in-memory storage                     │   │
│  │  Tracing UI: http://localhost:16686                              │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                         │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │  postgres (PostgreSQL)                                  :5432   │   │
│  │  (Application database)                                          │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Configuration Model

```
instrumentation.json
    │
    ▼
┌─────────────────────────────────────────────────────────────────┐
│ InstrumentationConfig                                           │
│                                                                 │
│  ├── packages: List<PackageConfig>                              │
│  │     │                                                        │
│  │     └── PackageConfig                                        │
│  │           ├── packageName: String     "com.myapp.service"    │
│  │           ├── recursive: boolean      true                   │
│  │           └── annotations: List<String>                      │
│  │                 ├── "org.springframework.stereotype.Service"  │
│  │                 └── "org.springframework.stereotype.Repo..." │
│  │                                                              │
│  └── instrumentations: List<MethodConfig>                       │
│        │                                                        │
│        └── MethodConfig                                         │
│              ├── className: String       "com.myapp.IOrderSvc"  │
│              ├── methodName: String      "createOrder"          │
│              └── attributes: List<AttributeDefinition>          │
│                    │                                            │
│                    └── AttributeDefinition                      │
│                          ├── argIndex: int          0           │
│                          ├── methodCall: String     "getCustId" │
│                          └── attributeName: String  "app.cid"   │
└─────────────────────────────────────────────────────────────────┘

                    Serialized to System Properties
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│ System Property Key:                                            │
│   "otel.dynamic.rules.com.myapp.IOrderSvc#createOrder"         │
│                                                                 │
│ System Property Value:                                          │
│   "0|getCustId|app.cid;0|getPaymentMethod|app.payment"          │
│                                                                 │
│ Deserialized at runtime to:                                     │
│   List<AttributeRule>                                           │
│     ├── AttributeRule(0, "getCustId", "app.cid")                │
│     └── AttributeRule(0, "getPaymentMethod", "app.payment")     │
│                                                                 │
│ Wrapped in RuleMatch when found via hierarchy lookup:           │
│   RuleMatch                                                     │
│     ├── sourceClassName: "com.myapp.IOrderSvc"                  │
│     ├── rules: [AttributeRule(...), ...]                        │
│     └── fromInterface: true                                     │
└─────────────────────────────────────────────────────────────────┘
```
