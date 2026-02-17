package com.otel.dynamic.extension;

import com.google.auto.service.AutoService;
import com.otel.dynamic.config.ConfigurationManager;
import com.otel.dynamic.config.model.AttributeDefinition;
import com.otel.dynamic.config.model.MethodConfig;
import com.otel.dynamic.config.model.ReturnValueAttribute;
import com.otel.dynamic.jmx.ConfigManager;
import com.otel.dynamic.util.Logger;
import com.otel.dynamic.agent.DynamicInstrumentationConfig;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        int configuredClasses = 0;
        if (configManager.getConfig() != null && configManager.getConfig().getInstrumentations() != null) {
            DynamicInstrumentationConfig.clear();
            for (MethodConfig mc : configManager.getConfig().getInstrumentations()) {
                configuredClasses++;

                // Convert AttributeDefinitions to simple AttributeRules (no Jackson dependency)
                List<DynamicInstrumentationConfig.AttributeRule> rules = new ArrayList<>();
                if (mc.getAttributes() != null) {
                    for (AttributeDefinition attr : mc.getAttributes()) {
                        rules.add(new DynamicInstrumentationConfig.AttributeRule(
                                attr.getArgIndex(), attr.getMethodCall(), attr.getAttributeName()));
                    }
                }
                DynamicInstrumentationConfig.register(mc.getClassName(), mc.getMethodName(), rules);

                // Register return value attribute extraction rules
                if (mc.getReturnValueAttributes() != null && !mc.getReturnValueAttributes().isEmpty()) {
                    List<DynamicInstrumentationConfig.ReturnValueRule> returnRules = new ArrayList<>();
                    for (ReturnValueAttribute attr : mc.getReturnValueAttributes()) {
                        returnRules.add(new DynamicInstrumentationConfig.ReturnValueRule(
                                attr.getMethodCall(), attr.getAttributeName()));
                    }
                    DynamicInstrumentationConfig.registerReturn(mc.getClassName(), mc.getMethodName(), returnRules);
                }
            }
        }

        Logger.info("Dynamic instrumentation: " + configuredClasses + " method rules configured");

        List<TypeInstrumentation> instrumentations = new ArrayList<>();

        // Use only GlobalTypeInstrumentation - it handles all matching logic:
        // - Package-level instrumentation with annotation filtering
        // - Explicit class/method configuration
        // - Interface-based instrumentation (via hierarchy check)
        //
        // IMPORTANT: Do NOT add DynamicTypeInstrumentation or PackageTypeInstrumentation
        // here, as they would cause duplicate instrumentation (same advice applied multiple times).
        instrumentations.add(new GlobalTypeInstrumentation());
        Logger.info("  Registered GlobalTypeInstrumentation (handles all matching)");

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
                "com.otel.dynamic.agent.DynamicInstrumentationConfig$RuleMatch",
                "com.otel.dynamic.agent.DynamicInstrumentationConfig$ReturnValueRule",
                "com.otel.dynamic.util.ReflectionHelper"
        );
    }
}
