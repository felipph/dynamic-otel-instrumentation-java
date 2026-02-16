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

        List<TypeInstrumentation> instrumentations = new ArrayList<>();

        // IMPORTANT: Always register GlobalTypeInstrumentation first.
        // This provides dynamic matching based on ConfigurationManager, which enables
        // hot-reload functionality. When configuration changes and retransformClasses()
        // is called, the GlobalTypeInstrumentation will re-evaluate matches against
        // the updated configuration.
        instrumentations.add(new GlobalTypeInstrumentation());
        Logger.info("  Registered GlobalTypeInstrumentation (dynamic matching enabled)");

        // 1. Per-class instrumentations from the "instrumentations" section
        for (Map.Entry<String, List<String>> entry : classToMethods.entrySet()) {
            instrumentations.add(new DynamicTypeInstrumentation(entry.getKey(), entry.getValue()));
            Logger.info("  Registered class instrumentation: " + entry.getKey()
                    + " methods=" + entry.getValue());
        }

        // 2. Package-level instrumentations from the "packages" section
        if (configManager.getConfig() != null && configManager.getConfig().getPackages() != null) {
            for (com.otel.dynamic.config.model.PackageConfig pkg : configManager.getConfig().getPackages()) {
                instrumentations.add(new PackageTypeInstrumentation(
                        pkg.getPackageName(), pkg.isRecursive(), pkg.getAnnotations()));
                Logger.info("  Registered package instrumentation: " + pkg.getPackageName()
                        + " recursive=" + pkg.isRecursive());
            }
        }

        Logger.info("Total TypeInstrumentation instances: " + instrumentations.size());
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
        return Arrays.asList(
                "com.otel.dynamic.extension.DynamicAdvice",
                "com.otel.dynamic.agent.DynamicInstrumentationConfig",
                "com.otel.dynamic.agent.DynamicInstrumentationConfig$AttributeRule",
                "com.otel.dynamic.agent.DynamicInstrumentationConfig$RuleMatch"
        );
    }
}
