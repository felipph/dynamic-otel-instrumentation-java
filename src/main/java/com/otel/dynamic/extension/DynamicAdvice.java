package com.otel.dynamic.extension;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import com.otel.dynamic.agent.DynamicInstrumentationConfig;
import com.otel.dynamic.util.ReflectionHelper;

import java.lang.reflect.Method;
import java.util.List;

/**
 * ByteBuddy advice class for dynamic instrumentation.
 *
 * Creates OpenTelemetry spans for methods matched by DynamicTypeInstrumentation.
 * Since the type+method matchers already guarantee this advice only fires on
 * configured methods, we always create a span here.
 *
 * Uses GlobalOpenTelemetry.getTracer() which is provided by the OTel Java Agent
 * and works correctly across classloader boundaries.
 *
 * NOTE: Uses @Advice.Origin("#t") and @Advice.Origin("#m") instead of
 * @Advice.Origin Method to avoid expensive Class.getMethod() calls,
 * as recommended by OTel instrumentation docs.
 *
 * Custom attribute extraction: Looks up attribute rules from DynamicInstrumentationConfig
 * and uses reflection to invoke the configured methods on method arguments.
 */
public class DynamicAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Scope onEnter(
            @Advice.Origin("#t") String className,
            @Advice.Origin("#m") String methodName,
            @Advice.AllArguments Object[] args,
            @Advice.Local("otelSpan") Span span,
            @Advice.Local("returnRules") List<DynamicInstrumentationConfig.ReturnValueRule> returnRules) {

        // #t returns internal name with slashes (e.g. com/sample/app/Foo), convert to dots
        String dotClassName = className.replace('/', '.');

        // Get tracer from the OTel Java Agent's GlobalOpenTelemetry
        Tracer tracer = GlobalOpenTelemetry.getTracer("dynamic-instrumentation", "1.0.0");

        // Build span name (extract simple class name)
        int lastDot = dotClassName.lastIndexOf('.');
        String simpleClassName = lastDot >= 0 ? dotClassName.substring(lastDot + 1) : dotClassName;
        String spanName = simpleClassName + "." + methodName;

        // Create and start span
        span = tracer.spanBuilder(spanName)
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("code.namespace", dotClassName)
                .setAttribute("code.function", methodName)
                .startSpan();

        // Detect if this method was instrumented via an interface and set attribute
        // Uses findRulesForHierarchy for rule-based matches, and falls back to
        // checking declared methods on interfaces for package-level instrumentation
        DynamicInstrumentationConfig.RuleMatch ruleMatch =
                DynamicInstrumentationConfig.findRulesForHierarchy(dotClassName, methodName);
        List<DynamicInstrumentationConfig.AttributeRule> rules =
                ruleMatch != null ? ruleMatch.getRules() : null;
        if (ruleMatch != null && ruleMatch.isFromInterface()) {
            span.setAttribute("code.instrumented.interface", ruleMatch.getSourceClassName());
        } else if (ruleMatch == null) {
            // No explicit rules — this is likely a package-level instrumented method.
            // Check if the method is declared by an interface this class implements.
            try {
                Class<?> runtimeClass = Class.forName(dotClassName, false,
                        Thread.currentThread().getContextClassLoader());
                for (Class<?> iface : runtimeClass.getInterfaces()) {
                    try {
                        iface.getMethod(methodName, (Class<?>[]) null);
                        span.setAttribute("code.instrumented.interface", iface.getName());
                        break;
                    } catch (NoSuchMethodException ignored) {
                        // method not declared by this interface, try next
                    }
                }
            } catch (Exception ignored) {
                // skip
            }
        }

        // Store return value rules for use in onExit
        returnRules = DynamicInstrumentationConfig.getReturnRulesForHierarchy(dotClassName, methodName);

        if (rules != null && args != null) {
            for (DynamicInstrumentationConfig.AttributeRule rule : rules) {
                try {
                    int idx = rule.getArgIndex();
                    if (idx >= 0 && idx < args.length && args[idx] != null) {
                        Object arg = args[idx];
                        String methodCallName = rule.getMethodCall();
                        String value;

                        // No methodCall or "toString" -> use the argument value directly
                        if (methodCallName == null || methodCallName.isEmpty() || "toString".equals(methodCallName)) {
                            value = arg.toString();
                        } else if (methodCallName.contains(".")) {
                            // Chained method call: use ReflectionHelper for traversal
                            value = ReflectionHelper.invokeMethodChain(arg, methodCallName)
                                    .map(Object::toString)
                                    .orElse(null);
                        } else {
                            // Single method call (existing logic)
                            Method m = arg.getClass().getMethod(methodCallName);
                            Object result = m.invoke(arg);
                            value = result != null ? result.toString() : null;
                        }

                        if (value != null) {
                            span.setAttribute(rule.getAttributeName(), value);
                        }
                    }
                } catch (Exception ignored) {
                    // Silently skip attribute extraction failures to avoid breaking the application
                }
            }
        }

        return span.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
            @Advice.Return(typing = Assigner.Typing.DYNAMIC, readOnly = false) Object returnValue,
            @Advice.Local("otelSpan") Span span,
            @Advice.Local("returnRules") List<DynamicInstrumentationConfig.ReturnValueRule> returnRules,
            @Advice.Enter Scope scope,
            @Advice.Thrown Throwable throwable) {

        if (scope != null) {
            scope.close();
        }

        if (span != null) {
            // Extract return value attributes
            if (returnRules != null && returnValue != null) {
                for (DynamicInstrumentationConfig.ReturnValueRule rule : returnRules) {
                    try {
                        String methodCallName = rule.getMethodCall();
                        String value;

                        if (methodCallName == null || methodCallName.isEmpty() || "toString".equals(methodCallName)) {
                            value = returnValue.toString();
                        } else if (methodCallName.contains(".")) {
                            // Chained method call
                            value = ReflectionHelper.invokeMethodChain(returnValue, methodCallName)
                                    .map(Object::toString)
                                    .orElse(null);
                        } else {
                            // Single method call
                            Method m = returnValue.getClass().getMethod(methodCallName);
                            Object result = m.invoke(returnValue);
                            value = result != null ? result.toString() : null;
                        }

                        if (value != null) {
                            span.setAttribute(rule.getAttributeName(), value);
                        }
                    } catch (Exception ignored) {
                        // Silently skip attribute extraction failures
                    }
                }
            }

            if (throwable != null) {
                span.setStatus(StatusCode.ERROR, throwable.getMessage());
                span.recordException(throwable);
            }
            span.end();
        }
    }
}
