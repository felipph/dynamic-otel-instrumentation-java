package com.otel.dynamic.extension;

import com.google.auto.service.AutoService;
import com.otel.dynamic.config.ConfigurationManager;
import com.otel.dynamic.config.model.AttributeDefinition;
import com.otel.dynamic.config.model.MethodConfig;
import com.otel.dynamic.jmx.ConfigManager;
import com.otel.dynamic.util.Logger;
import com.otel.dynamic.agent.DynamicInstrumentationConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenTelemetry Java Agent extension module for configuration-driven instrumentation.
 *
 * This module reads instrumentation.json and dynamically creates TypeInstrumentation
 * instances for each configured class/method pair. The OTel Java Agent handles all
 * classloader isolation, bootstrap injection, and advice inlining.
 */
@AutoService(InstrumentationModule.class)
public class ConfigDrivenInstrumentationModule extends InstrumentationModule {

    public ConfigDrivenInstrumentationModule() {
        super("dynamic-config", "dynamic-instrumentation");
    }

    @Override
    public int order() {
        // Run after standard instrumentations
        return 1;
    }

    @Override
    public List<TypeInstrumentation> typeInstrumentations() {
        Logger.info("Loading dynamic instrumentation configuration...");

        // Initialize ConfigurationManager with the config path
        String configPath = System.getProperty("instrumentation.config.path");
        ConfigurationManager configManager;
        if (configPath != null && !configPath.isEmpty()) {
            configManager = ConfigurationManager.initialize(configPath);
        } else {
            configManager = ConfigurationManager.getInstance();
        }

        // Register JMX MBean for runtime management (reload, debug, etc.)
        try {
            ConfigManager.initialize(configManager);
        } catch (Exception e) {
            Logger.error("Failed to initialize ConfigManager MBean", e);
        }

        // Populate the DynamicInstrumentationConfig registry so the inlined advice
        // can access attribute extraction rules without needing ConfigurationManager/Jackson
        Map<String, List<String>> classToMethods = new HashMap<>();
        if (configManager.getConfig() != null && configManager.getConfig().getInstrumentations() != null) {
            DynamicInstrumentationConfig.clear();
            for (MethodConfig mc : configManager.getConfig().getInstrumentations()) {
                classToMethods
                        .computeIfAbsent(mc.getClassName(), k -> new ArrayList<>())
                        .add(mc.getMethodName());

                // Convert AttributeDefinitions to simple AttributeRules (no Jackson dependency)
                List<DynamicInstrumentationConfig.AttributeRule> rules = new ArrayList<>();
                if (mc.getAttributes() != null) {
                    for (AttributeDefinition attr : mc.getAttributes()) {
                        rules.add(new DynamicInstrumentationConfig.AttributeRule(
                                attr.getArgIndex(), attr.getMethodCall(), attr.getAttributeName()));
                    }
                }
                DynamicInstrumentationConfig.register(mc.getClassName(), mc.getMethodName(), rules);
            }
        }

        Logger.info("Dynamic instrumentation: " + classToMethods.size() + " classes configured");

        // Use a single GlobalTypeInstrumentation that delegates to ConfigurationManager for matching.
        // This allows adding new classes/packages at runtime via retransformClasses().
        List<TypeInstrumentation> instrumentations = new ArrayList<>();
        instrumentations.add(new GlobalTypeInstrumentation());
        
        return instrumentations;
    }

    @Override
    public boolean isHelperClass(String className) {
        return className.startsWith("com.otel.dynamic.");
    }

    @Override
    public List<String> getAdditionalHelperClassNames() {
        // These classes will be injected into the application classloader
        // so that inlined advice code can resolve them at runtime.
        // DynamicInstrumentationConfig and its inner classes are now on bootstrap classpath
        // via DynamicAgent, so we don't need to inject them here.
        return Arrays.asList(
                "com.otel.dynamic.extension.DynamicAdvice"
        );
    }
}
