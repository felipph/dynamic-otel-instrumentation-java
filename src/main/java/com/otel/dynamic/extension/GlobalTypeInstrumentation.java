package com.otel.dynamic.extension;

import com.otel.dynamic.config.ConfigurationManager;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * A global type instrumentation that matches classes dynamically based on the
 * current configuration in ConfigurationManager.
 *
 * This allows classes to be instrumented or un-instrumented at runtime when
 * configuration changes, without needing to register new TypeInstrumentation instances.
 */
public class GlobalTypeInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
        return new ElementMatcher.Junction.AbstractBase<TypeDescription>() {
            @Override
            public boolean matches(TypeDescription target) {
                // Walk up the hierarchy (self -> superclass -> ...)
                // At each level, check the class itself and its interfaces
                TypeDescription current = target;
                try {
                    while (current != null && !current.represents(Object.class)) {
                        // 1. Check the class itself
                        if (ConfigurationManager.getInstance().isClassInstrumented(current.getName())) {
                            return true;
                        }

                        // 2. Check interfaces of this class
                        for (TypeDescription.Generic iface : current.getInterfaces()) {
                            if (ConfigurationManager.getInstance().isClassInstrumented(iface.asErasure().getName())) {
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
        };
    }

    @Override
    public void transform(TypeTransformer typeTransformer) {
        // Apply advice to methods that are configured for instrumentation
        typeTransformer.applyAdviceToMethod(
            new ElementMatcher.Junction.AbstractBase<MethodDescription>() {
                @Override
                public boolean matches(MethodDescription target) {
                    String methodName = target.getName();
                    TypeDescription declaringType = target.getDeclaringType().asErasure();
                    
                    // Walk hierarchy to check if this method is configured on any type in the chain
                    TypeDescription current = declaringType;
                    try {
                        while (current != null && !current.represents(Object.class)) {
                            // 1. Check class itself
                            if (ConfigurationManager.getInstance().isMethodInstrumented(current.getName(), methodName)) {
                                return true;
                            }

                            // 2. Check interfaces
                            for (TypeDescription.Generic iface : current.getInterfaces()) {
                                if (ConfigurationManager.getInstance().isMethodInstrumented(iface.asErasure().getName(), methodName)) {
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
            },
            DynamicAdvice.class.getName()
        );
    }
}
