package com.otel.dynamic.extension;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

import java.util.List;

/**
 * Type instrumentation that matches all classes in a given package
 * and instruments all their declared (non-synthetic, non-constructor) methods.
 *
 * If {@code recursive} is true, sub-packages are also matched.
 * If {@code annotations} is non-empty, only classes annotated with at least
 * one of the listed annotations will be instrumented.
 */
public class PackageTypeInstrumentation implements TypeInstrumentation {

    private final String packageName;
    private final boolean recursive;
    private final List<String> annotations;

    public PackageTypeInstrumentation(String packageName, boolean recursive, List<String> annotations) {
        this.packageName = packageName;
        this.recursive = recursive;
        this.annotations = annotations;
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        // Build the package matcher
        ElementMatcher.Junction<TypeDescription> packageMatcher;
        if (recursive) {
            packageMatcher = ElementMatchers.nameStartsWith(packageName + ".");
        } else {
            packageMatcher = new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
                @Override
                public boolean matches(TypeDescription target) {
                    String name = target.getName();
                    if (!name.startsWith(packageName + ".")) {
                        return false;
                    }
                    String remainder = name.substring(packageName.length() + 1);
                    return !remainder.contains(".");
                }
            };
        }

        // If annotations are specified, add annotation filter
        if (annotations != null && !annotations.isEmpty()) {
            ElementMatcher.Junction<TypeDescription> annotationMatcher = null;
            for (String annotation : annotations) {
                ElementMatcher.Junction<TypeDescription> single =
                        ElementMatchers.isAnnotatedWith(ElementMatchers.named(annotation));
                if (annotationMatcher == null) {
                    annotationMatcher = single;
                } else {
                    annotationMatcher = annotationMatcher.or(single);
                }
            }
            packageMatcher = packageMatcher.and(annotationMatcher);
        }

        return packageMatcher;
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        // Instrument all declared public/protected/package-private methods
        // Exclude constructors, synthetic methods, and Object methods
        typeTransformer.applyAdviceToMethod(
                ElementMatchers.not(ElementMatchers.isConstructor())
                        .and(ElementMatchers.not(ElementMatchers.isSynthetic()))
                        .and(ElementMatchers.not(ElementMatchers.named("equals")))
                        .and(ElementMatchers.not(ElementMatchers.named("hashCode")))
                        .and(ElementMatchers.not(ElementMatchers.named("toString")))
                        .and(ElementMatchers.not(ElementMatchers.named("getClass")))
                        .and(ElementMatchers.isDeclaredBy(
                                ElementMatchers.nameStartsWith(packageName + "."))),
                DynamicAdvice.class.getName());
    }
}
