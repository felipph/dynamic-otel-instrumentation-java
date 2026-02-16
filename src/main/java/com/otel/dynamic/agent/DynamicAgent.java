package com.otel.dynamic.agent;

import java.lang.instrument.Instrumentation;
import java.util.jar.JarFile;
import java.io.File;
import java.security.CodeSource;

/**
 * Java Agent entry point to capture the Instrumentation instance.
 */
public class DynamicAgent {
    public static void premain(String agentArgs, Instrumentation inst) {
        setupInstrumentation(inst);
    }

    public static void agentmain(String agentArgs, Instrumentation inst) {
        setupInstrumentation(inst);
    }

    private static void setupInstrumentation(Instrumentation inst) {
        try {
            // 1. Add this JAR to the bootstrap classloader
            CodeSource codeSource = DynamicAgent.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                File jarFile = new File(codeSource.getLocation().toURI());
                inst.appendToBootstrapClassLoaderSearch(new JarFile(jarFile));
            }
            
            // 2. Load InstrumentationAccessor from the bootstrap classloader (loader=null)
            // and set the instrumentation instance on IT, so it's visible to the OTel extension.
            Class<?> accessorClass = Class.forName("com.otel.dynamic.agent.InstrumentationAccessor", true, null);
            java.lang.reflect.Method setMethod = accessorClass.getMethod("setInstrumentation", Instrumentation.class);
            setMethod.invoke(null, inst);
            
            System.out.println("[DynamicAgent] Instrumentation instance registered in bootstrap classloader");
            
        } catch (Exception e) {
            System.err.println("[DynamicAgent] Failed to setup instrumentation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
