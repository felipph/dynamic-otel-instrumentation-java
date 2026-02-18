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
                    // Check concreteOnly for explicit class config
                    if (shouldSkipAbstractClass(target, className)) {
                        return false;
                    }
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
                            String interfaceName = iface.asErasure().getName();
                            if (ConfigurationManager.getInstance().hasExplicitClassConfig(interfaceName)) {
                                // Check concreteOnly for interface-based config
                                if (shouldSkipAbstractClassForInterface(target, interfaceName)) {
                                    return false;
                                }
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

            /**
             * Check if this abstract class should be skipped based on concreteOnly configuration.
             * This checks if any method in the configuration for this class has concreteOnly=true.
             */
            private boolean shouldSkipAbstractClass(TypeDescription target, String configuredClassName) {
                // If the class is not abstract, never skip
                if (!target.isAbstract()) {
                    return false;
                }

                // Check if concreteOnly is enabled for this class (any method)
                // We need to check all configured methods for this class
                return ConfigurationManager.getInstance().isConcreteOnlyForHierarchy(target.getName(), "*");
            }

            /**
             * Check if this abstract class should be skipped when matched via an interface.
             * This checks if the interface configuration has concreteOnly=true.
             */
            private boolean shouldSkipAbstractClassForInterface(TypeDescription target, String interfaceName) {
                // If the class is not abstract, never skip
                if (!target.isAbstract()) {
                    return false;
                }

                // Check if concreteOnly is enabled globally
                if (ConfigurationManager.getInstance().isConcreteOnly(interfaceName, "*")) {
                    return true;
                }

                // Also check if global concreteOnly is set
                return ConfigurationManager.getInstance().getCurrentConfig().getConfig().getConcreteOnly() != null
                        && ConfigurationManager.getInstance().getCurrentConfig().getConfig().getConcreteOnly();
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
                        // Check concreteOnly for explicit method config
                        if (shouldSkipAbstractMethod(declaringType, methodName)) {
                            return false;
                        }
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
                                String interfaceName = iface.asErasure().getName();
                                if (ConfigurationManager.getInstance().isMethodInstrumented(interfaceName, methodName)) {
                                    // Check concreteOnly for interface-based config
                                    if (shouldSkipAbstractMethodForInterface(declaringType, interfaceName, methodName)) {
                                        return false;
                                    }
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

                /**
                 * Check if this abstract class method should be skipped based on concreteOnly.
                 */
                private boolean shouldSkipAbstractMethod(TypeDescription declaringType, String methodName) {
                    // If the class is not abstract, never skip
                    if (!declaringType.isAbstract()) {
                        return false;
                    }

                    // Check if concreteOnly is enabled for this method
                    return ConfigurationManager.getInstance().isConcreteOnly(declaringType.getName(), methodName);
                }

                /**
                 * Check if this abstract class method should be skipped when matched via an interface.
                 */
                private boolean shouldSkipAbstractMethodForInterface(TypeDescription declaringType, String interfaceName, String methodName) {
                    // If the class is not abstract, never skip
                    if (!declaringType.isAbstract()) {
                        return false;
                    }

                    // Check if concreteOnly is enabled for this interface+method
                    if (ConfigurationManager.getInstance().isConcreteOnly(interfaceName, methodName)) {
                        return true;
                    }

                    // Also check if global concreteOnly is set
                    return ConfigurationManager.getInstance().getCurrentConfig().getConfig().getConcreteOnly() != null
                            && ConfigurationManager.getInstance().getCurrentConfig().getConfig().getConcreteOnly();
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
