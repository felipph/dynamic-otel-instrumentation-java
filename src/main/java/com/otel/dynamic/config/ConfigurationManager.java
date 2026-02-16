package com.otel.dynamic.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.otel.dynamic.config.model.InstrumentationConfig;
import com.otel.dynamic.config.model.MethodConfig;
import com.otel.dynamic.util.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the loading, parsing, and access to instrumentation configuration.
 *
 * This class is thread-safe and provides atomic reloads without blocking reads.
 * Configuration is stored as an immutable snapshot for thread-safe access.
 */
public class ConfigurationManager {

    private static final String DEFAULT_CONFIG_PATH = "/opt/otel/config/instrumentation.json";
    private static final String ENV_CONFIG_PATH = "INSTRUMENTATION_CONFIG_PATH";

    private static volatile ConfigurationManager instance;

    private final ObjectMapper objectMapper;
    private final AtomicReference<ConfigSnapshot> currentConfig;
    private final String configFilePath;

    // Configuration change listeners
    private final java.util.List<ConfigurationChangeListener> listeners;

    /**
     * Private constructor for singleton pattern
     */
    private ConfigurationManager(String configFilePath) {
        this.objectMapper = new ObjectMapper();
        // Ignore unknown properties for forward compatibility
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.currentConfig = new AtomicReference<>();
        this.configFilePath = configFilePath != null ? configFilePath : getConfigPathFromEnv();
        this.listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

        // Load initial configuration
        loadConfiguration();
    }

    /**
     * Get the singleton instance of ConfigurationManager
     */
    public static synchronized ConfigurationManager getInstance() {
        if (instance == null) {
            instance = new ConfigurationManager(null);
        }
        return instance;
    }

    /**
     * Initialize the singleton with a specific config file path
     */
    public static synchronized ConfigurationManager initialize(String configFilePath) {
        if (instance == null) {
            instance = new ConfigurationManager(configFilePath);
        }
        return instance;
    }

    /**
     * Reset the singleton instance (primarily for testing)
     */
    public static synchronized void reset() {
        instance = null;
    }

    /**
     * Get config file path from environment variable or use default
     */
    private String getConfigPathFromEnv() {
        String path = System.getenv(ENV_CONFIG_PATH);
        return path != null ? path : DEFAULT_CONFIG_PATH;
    }

    /**
     * Load configuration from the configured file path
     */
    public void loadConfiguration() {
        loadConfiguration(configFilePath);
    }

    /**
     * Load configuration from a specific file path
     */
    public void loadConfiguration(String path) {
        try {
            Logger.info("Loading configuration from: " + path);

            File configFile = new File(path);
            if (!configFile.exists()) {
                Logger.warn("Configuration file not found: " + path + ". Using empty configuration.");
                updateConfig(new InstrumentationConfig());
                return;
            }

            InstrumentationConfig config = objectMapper.readValue(configFile, InstrumentationConfig.class);
            updateConfig(config);

            Logger.info("Configuration loaded successfully. Instrumentations defined: " + config.size());

        } catch (IOException e) {
            Logger.error("Failed to load configuration from: " + path, e);
            Logger.warn("Using empty configuration due to load failure.");
            updateConfig(new InstrumentationConfig());
        }
    }

    /**
     * Load configuration from an InputStream
     */
    public void loadConfiguration(InputStream inputStream) {
        try {
            InstrumentationConfig config = objectMapper.readValue(inputStream, InstrumentationConfig.class);
            updateConfig(config);
            Logger.info("Configuration loaded from input stream. Instrumentations defined: " + config.size());
        } catch (IOException e) {
            Logger.error("Failed to load configuration from input stream", e);
            updateConfig(new InstrumentationConfig());
        }
    }

    /**
     * Update the current configuration atomically and notify listeners
     */
    private void updateConfig(InstrumentationConfig newConfig) {
        ConfigSnapshot newSnapshot = new ConfigSnapshot(newConfig);
        currentConfig.set(newSnapshot);

        // Notify listeners of configuration change
        for (ConfigurationChangeListener listener : listeners) {
            try {
                listener.onConfigurationChanged(newConfig);
            } catch (Exception e) {
                Logger.error("Error notifying configuration listener", e);
            }
        }
    }

    /**
     * Get the current configuration snapshot
     */
    public ConfigSnapshot getCurrentConfig() {
        return currentConfig.get();
    }

    /**
     * Get the instrumentation config (for backward compatibility)
     */
    public InstrumentationConfig getConfig() {
        ConfigSnapshot snapshot = currentConfig.get();
        return snapshot != null ? snapshot.getConfig() : new InstrumentationConfig();
    }

    /**
     * Find configuration for a specific method
     */
    public MethodConfig getConfigFor(Method method) {
        ConfigSnapshot snapshot = currentConfig.get();
        if (snapshot == null) {
            return null;
        }

        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        return snapshot.getConfigFor(className, methodName);
    }

    /**
     * Find configuration for a specific class and method name
     */
    public MethodConfig getConfigFor(String className, String methodName) {
        ConfigSnapshot snapshot = currentConfig.get();
        if (snapshot == null) {
            return null;
        }

        return snapshot.getConfigFor(className, methodName);
    }

    /**
     * Add a configuration change listener
     */
    public void addListener(ConfigurationChangeListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a configuration change listener
     */
    public void removeListener(ConfigurationChangeListener listener) {
        listeners.remove(listener);
    }

    /**
     * Get the configuration file path
     */
    public String getConfigFilePath() {
        return configFilePath;
    }

    /**
     * Check if a class should be instrumented (either explicitly or via package config)
     */
    public boolean isClassInstrumented(String className) {
        ConfigSnapshot snapshot = currentConfig.get();
        if (snapshot == null) return false;

        // Check exact class match
        if (snapshot.hasConfigForClass(className)) {
            return true;
        }

        // Check package match
        if (snapshot.getConfig().getPackages() != null) {
            for (com.otel.dynamic.config.model.PackageConfig pkg : snapshot.getConfig().getPackages()) {
                if (className.startsWith(pkg.getPackageName() + ".")) {
                    // If recursive, it matches
                    if (pkg.isRecursive()) return true;
                    // If not recursive, must be direct child
                    String remainder = className.substring(pkg.getPackageName().length() + 1);
                    if (!remainder.contains(".")) return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if a method should be instrumented
     */
    public boolean isMethodInstrumented(String className, String methodName) {
        ConfigSnapshot snapshot = currentConfig.get();
        if (snapshot == null) return false;

        // Check explicit method config
        if (snapshot.getConfigFor(className, methodName) != null) {
            return true;
        }

        // Check package config (implies all public/protected/package-private methods)
        if (snapshot.getConfig().getPackages() != null) {
            for (com.otel.dynamic.config.model.PackageConfig pkg : snapshot.getConfig().getPackages()) {
                if (className.startsWith(pkg.getPackageName() + ".")) {
                    boolean match;
                    if (pkg.isRecursive()) {
                        match = true;
                    } else {
                        String remainder = className.substring(pkg.getPackageName().length() + 1);
                        match = !remainder.contains(".");
                    }
                    
                    // For package instrumentation, we usually want to exclude some methods (like constructors, synthetic)
                    // But the matcher caller (ByteBuddy) will ask "is this method instrumented?"
                    // The actual exclusion logic (no constructors, etc) is usually in the matcher construction.
                    // Here we just say "yes, this method falls under the scope".
                    // The caller should apply additional filtering (like isConstructor).
                    if (match) return true;
                }
            }
        }
        return false;
    }

    /**
     * Immutable snapshot of the configuration for thread-safe access
     */
    public static class ConfigSnapshot {
        private final InstrumentationConfig config;
        private final Map<String, MethodConfig> methodConfigMap;
        private final java.util.Set<String> configuredClasses;

        public ConfigSnapshot(InstrumentationConfig config) {
            this.config = config;
            this.methodConfigMap = new ConcurrentHashMap<>();
            this.configuredClasses = ConcurrentHashMap.newKeySet();

            // Build lookup map for fast access
            if (config != null && config.getInstrumentations() != null) {
                for (MethodConfig methodConfig : config.getInstrumentations()) {
                    String key = buildKey(methodConfig.getClassName(), methodConfig.getMethodName());
                    methodConfigMap.put(key, methodConfig);
                    configuredClasses.add(methodConfig.getClassName());
                }
            }
        }

        public boolean hasConfigForClass(String className) {
            return configuredClasses.contains(className);
        }

        public InstrumentationConfig getConfig() {
            return config;
        }

        public MethodConfig getConfigFor(String className, String methodName) {
            String key = buildKey(className, methodName);
            return methodConfigMap.get(key);
        }

        private String buildKey(String className, String methodName) {
            return className + "." + methodName;
        }
    }

    /**
     * Interface for configuration change listeners
     */
    public interface ConfigurationChangeListener {
        void onConfigurationChanged(InstrumentationConfig newConfig);
    }
}
