package com.otel.dynamic.extension;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.List;

/**
 * Per-class type instrumentation that applies DynamicAdvice to configured methods.
 *
 * Each instance targets a single class and instruments one or more methods
 * as defined in instrumentation.json.
 */
public class DynamicTypeInstrumentation implements TypeInstrumentation {

    private final String className;
    private final List<String> methodNames;

    public DynamicTypeInstrumentation(String className, List<String> methodNames) {
        this.className = className;
        this.methodNames = methodNames;
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        // hasSuperType matches the named class itself AND all classes that
        // implement/extend it, so configuring an interface instruments all implementations
        return ElementMatchers.hasSuperType(ElementMatchers.named(className));
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        for (String methodName : methodNames) {
            typeTransformer.applyAdviceToMethod(
                    ElementMatchers.named(methodName),
                    DynamicAdvice.class.getName());
        }
    }
}
