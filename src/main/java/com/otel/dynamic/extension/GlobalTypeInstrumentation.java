package com.otel.dynamic.extension;

import java.util.List;

import com.otel.dynamic.config.ConfigurationManager;
import com.otel.dynamic.config.model.PackageConfig;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * A global type instrumentation that matches classes dynamically based on the
 * current configuration in ConfigurationManager.
 *
 * This allows classes to be instrumented or un-instrumented at runtime when
 * configuration changes, without needing to register new TypeInstrumentation instances.
 *
 * IMPORTANT: This instrumentation checks configuration at match time, enabling
 * hot-reload functionality. When retransformClasses() is called after a config
 * change, this matcher will re-evaluate against the updated configuration.
 */
public class GlobalTypeInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        return new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
            @Override
            public boolean matches(TypeDescription target) {
                String className = target.getName();

                // 1. Check explicit class configuration (from "instrumentations" section)
                // These don't require annotation filtering
                if (ConfigurationManager.getInstance().hasExplicitClassConfig(className)) {
                    return true;
                }

                // 2. Check package configuration with annotation filtering
                PackageConfig pkgConfig = ConfigurationManager.getInstance().getMatchingPackageConfig(className);
                if (pkgConfig != null) {
                    List<String> annotations = pkgConfig.getAnnotations();
                    // If no annotations specified, match all classes in package
                    if (annotations == null || annotations.isEmpty()) {
                        return true;
                    }
                    // Check if class has any of the required annotations
                    for (String annotation : annotations) {
                        if (hasAnnotation(target, annotation)) {
                            return true;
                        }
                    }
                    // Class is in package but doesn't have required annotation
                    return false;
                }

                // 3. Check hierarchy for interface-based configuration
                TypeDescription current = target;
                try {
                    while (current != null && !current.represents(Object.class)) {
                        // Check interfaces of this class
                        for (TypeDescription.Generic iface : current.getInterfaces()) {
                            if (ConfigurationManager.getInstance().hasExplicitClassConfig(iface.asErasure().getName())) {
                                return true;
                            }
                        }

                        // Move to superclass
                        TypeDescription.Generic superClassGen = current.getSuperClass();
                        current = (superClassGen != null) ? superClassGen.asErasure() : null;
                    }
                } catch (Exception e) {
                    // Ignore resolution errors
                }

                return false;
            }

            private boolean hasAnnotation(TypeDescription target, String annotationName) {
                try {
                    // Use ByteBuddy's annotation matcher which handles inheritance
                    return ElementMatchers.isAnnotatedWith(ElementMatchers.named(annotationName)).matches(target);
                } catch (Exception e) {
                    return false;
                }
            }
        };
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        // Apply advice to methods that are configured for instrumentation
        // Exclude constructors, synthetic methods, and common Object methods
        typeTransformer.applyAdviceToMethod(
            new ElementMatcher.Junction.AbstractBase<MethodDescription>() {
                @Override
                public boolean matches(MethodDescription target) {
                    // Skip constructors and synthetic methods
                    if (target.isConstructor() || target.isSynthetic()) {
                        return false;
                    }

                    String methodName = target.getName();

                    // Skip common Object methods
                    if (methodName.equals("equals") || methodName.equals("hashCode") ||
                        methodName.equals("toString") || methodName.equals("getClass")) {
                        return false;
                    }

                    TypeDescription declaringType = target.getDeclaringType().asErasure();
                    String declaringClassName = declaringType.getName();

                    // 1. Check explicit method configuration (no annotation filtering)
                    if (ConfigurationManager.getInstance().isMethodInstrumented(declaringClassName, methodName)) {
                        return true;
                    }

                    // 2. Check if this is a package-level instrumented class
                    // Must also verify annotation filtering
                    PackageConfig pkgConfig = ConfigurationManager.getInstance().getMatchingPackageConfig(declaringClassName);
                    if (pkgConfig != null) {
                        List<String> annotations = pkgConfig.getAnnotations();
                        // If no annotations filter, or if class has required annotation, match
                        if (annotations == null || annotations.isEmpty()) {
                            return true;
                        }
                        // Check annotation - must have at least one
                        for (String annotation : annotations) {
                            if (hasAnnotation(declaringType, annotation)) {
                                return true;
                            }
                        }
                        // Class is in package but doesn't have required annotation
                        return false;
                    }

                    // 3. Check hierarchy for interface-based configuration
                    TypeDescription current = declaringType;
                    try {
                        while (current != null && !current.represents(Object.class)) {
                            // Check interfaces
                            for (TypeDescription.Generic iface : current.getInterfaces()) {
                                if (ConfigurationManager.getInstance().isMethodInstrumented(
                                        iface.asErasure().getName(), methodName)) {
                                    return true;
                                }
                            }

                            // Move to superclass
                            TypeDescription.Generic superClassGen = current.getSuperClass();
                            current = (superClassGen != null) ? superClassGen.asErasure() : null;
                        }
                    } catch (Exception e) {
                        // Ignore resolution errors
                    }

                    return false;
                }

                private boolean hasAnnotation(TypeDescription target, String annotationName) {
                    try {
                        return ElementMatchers.isAnnotatedWith(ElementMatchers.named(annotationName)).matches(target);
                    } catch (Exception e) {
                        return false;
                    }
                }
            },
            DynamicAdvice.class.getName()
        );
    }
}
