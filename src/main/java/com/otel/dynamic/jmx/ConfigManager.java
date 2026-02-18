package com.otel.dynamic.jmx;

import com.otel.dynamic.agent.InstrumentationAccessor;
import com.otel.dynamic.agent.InstrumentationDiff;
import com.otel.dynamic.config.ConfigurationManager;
import com.otel.dynamic.config.model.AttributeDefinition;
import com.otel.dynamic.config.model.InstrumentationConfig;
import com.otel.dynamic.config.model.MethodConfig;
import com.otel.dynamic.config.model.ReturnValueAttribute;
import com.otel.dynamic.agent.DynamicInstrumentationConfig;
import com.otel.dynamic.util.Logger;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JMX implementation for configuration management.
 *
 * Provides runtime management capabilities for the instrumentation extension.
 * Allows reloading configuration, querying state, and controlling debug logging.
 */
public class ConfigManager implements ConfigManagerMBean {

    private static final String MBEAN_NAME = "com.otel.dynamic:type=ConfigManager";

    private final ConfigurationManager configManager;

    private static volatile ConfigManager instance;

    /**
     * Get the singleton instance of ConfigManager.
     */
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ConfigManager not initialized. Call initialize() first.");
        }
        return instance;
    }

    private static final long MBEAN_REGISTRATION_DELAY_MS = 30_000;
    private static final long MBEAN_REGISTRATION_RETRY_MS = 10_000;
    private static final int MBEAN_REGISTRATION_MAX_RETRIES = 12;

    /**
     * Initialize the ConfigManager with the required dependencies.
     * MBean registration is deferred to a background thread to avoid triggering
     * LogManager initialization during the OTel agent premain phase, which would
     * fail because JBoss LogManager is not yet on the classpath.
     */
    public static synchronized ConfigManager initialize(ConfigurationManager configManager) {
        if (instance == null) {
            instance = new ConfigManager(configManager);
            instance.deferMBeanRegistration();
        }
        return instance;
    }

    /**
     * Schedule MBean registration on a daemon thread after a delay,
     * giving WildFly time to fully boot and set up its classloaders.
     */
    private void deferMBeanRegistration() {
        Thread registrationThread = new Thread(() -> {
            try {
                Logger.info("MBean registration deferred, waiting for server startup...");
                Thread.sleep(MBEAN_REGISTRATION_DELAY_MS);

                for (int attempt = 1; attempt <= MBEAN_REGISTRATION_MAX_RETRIES; attempt++) {
                    try {
                        registerMBean();
                        return;
                    } catch (Exception e) {
                        Logger.warn("MBean registration attempt " + attempt + " failed: " + e.getMessage());
                        if (attempt < MBEAN_REGISTRATION_MAX_RETRIES) {
                            Thread.sleep(MBEAN_REGISTRATION_RETRY_MS);
                        }
                    }
                }
                Logger.error("MBean registration failed after " + MBEAN_REGISTRATION_MAX_RETRIES + " attempts");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Logger.warn("MBean registration thread interrupted");
            }
        }, "DynamicInstrumentation-MBeanRegistration");
        registrationThread.setDaemon(true);
        registrationThread.start();
    }

    /**
     * Private constructor.
     */
    private ConfigManager(ConfigurationManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Register this MBean with the platform MBean server.
     */
    private void registerMBean() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName(MBEAN_NAME);

            if (!mbs.isRegistered(name)) {
                mbs.registerMBean(this, name);
                Logger.info("ConfigManager MBean registered: " + MBEAN_NAME);
            } else {
                Logger.warn("ConfigManager MBean already registered");
            }

        } catch (InstanceAlreadyExistsException e) {
            Logger.warn("ConfigManager MBean already exists, skipping registration");
        } catch (Exception e) {
            Logger.error("Failed to register ConfigManager MBean", e);
        }
    }

    /**
     * Unregister this MBean from the platform MBean server.
     */
    private void unregisterMBean() {
        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName(MBEAN_NAME);

            if (mbs.isRegistered(name)) {
                mbs.unregisterMBean(name);
                Logger.info("ConfigManager MBean unregistered");
            }

        } catch (Exception e) {
            Logger.error("Failed to unregister ConfigManager MBean", e);
        }
    }

    @Override
    public boolean reloadConfiguration() {
        try {
            Logger.info("Configuration reload requested via JMX");

            // 1. Snapshot current checksums before clearing
            Map<String, String> oldChecksums = DynamicInstrumentationConfig.getAllChecksums();
            Logger.debug("Snapshot of " + oldChecksums.size() + " existing instrumentation entries");

            // 2. Reload configuration from file
            configManager.loadConfiguration();

            // 3. Update DynamicInstrumentationConfig registry (populates new checksums)
            updateDynamicRegistry();

            // 4. Compute diff and trigger incremental retransformation
            retransformClassesIncremental(oldChecksums);

            return true;
        } catch (Exception e) {
            Logger.error("Failed to reload configuration via JMX", e);
            return false;
        }
    }
    
    private void updateDynamicRegistry() {
        try {
            InstrumentationConfig config = configManager.getConfig();
            if (config != null && config.getInstrumentations() != null) {
                DynamicInstrumentationConfig.clear();
                for (MethodConfig mc : config.getInstrumentations()) {
                    // Convert AttributeDefinitions to simple AttributeRules
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
                Logger.info("DynamicInstrumentationConfig registry updated");
            }
        } catch (Exception e) {
            Logger.error("Failed to update DynamicInstrumentationConfig registry", e);
        }
    }
    
    /**
     * Perform incremental retransformation by comparing old and new checksums.
     * Only classes affected by configuration changes are retransformed.
     *
     * @param oldChecksums checksum snapshot before configuration reload
     */
    private void retransformClassesIncremental(Map<String, String> oldChecksums) {
        Instrumentation inst = InstrumentationAccessor.getInstrumentation();
        if (inst == null) {
            Logger.warn("Instrumentation instance not available - cannot retransform classes. " +
                    "Make sure the agent is configured as a javaagent.");
            return;
        }

        // Compute diff between old and new configurations
        Map<String, String> newChecksums = DynamicInstrumentationConfig.getAllChecksums();
        InstrumentationDiff diff = InstrumentationDiff.compute(oldChecksums, newChecksums);

        Logger.info("Instrumentation diff: added/changed=" + diff.getAddedOrChanged().size() +
                ", removed=" + diff.getRemoved().size() +
                ", unchanged=" + diff.getUnchanged().size());

        // If no changes, skip retransformation entirely
        if (!diff.hasChanges()) {
            Logger.info("No configuration changes detected - skipping retransformation");
            return;
        }

        // Get all affected class#method entries
        Set<String> affected = diff.getAffected();
        Logger.info("Retransforming classes affected by " + affected.size() + " configuration changes...");

        try {
            Class<?>[] loadedClasses = inst.getAllLoadedClasses();
            Set<Class<?>> classesToRetransform = new HashSet<>();

            // Build a set of affected class names (without method)
            Set<String> affectedClassNames = new HashSet<>();
            for (String classMethod : affected) {
                int hashIndex = classMethod.indexOf('#');
                if (hashIndex > 0) {
                    affectedClassNames.add(classMethod.substring(0, hashIndex));
                }
            }

            for (Class<?> clazz : loadedClasses) {
                if (!inst.isModifiableClass(clazz)) {
                    continue;
                }

                String className = clazz.getName();

                // Check if this class is directly affected
                if (affectedClassNames.contains(className)) {
                    classesToRetransform.add(clazz);
                    continue;
                }

                // Check if class implements an affected interface or extends an affected class
                boolean isAffected = isClassAffectedByDiff(clazz, affectedClassNames);

                if (isAffected) {
                    classesToRetransform.add(clazz);
                }
            }

            if (!classesToRetransform.isEmpty()) {
                Logger.info("Retransforming " + classesToRetransform.size() + " classes (incremental)...");
                inst.retransformClasses(classesToRetransform.toArray(new Class<?>[0]));
                Logger.info("Incremental retransformation complete");
            } else {
                Logger.info("No matching loaded classes found for affected configurations");
            }
        } catch (Exception e) {
            Logger.error("Failed to retransform classes incrementally", e);
        }
    }

    /**
     * Check if a class is affected by the diff through its hierarchy (interfaces/superclasses).
     */
    private boolean isClassAffectedByDiff(Class<?> clazz, Set<String> affectedClassNames) {
        // Check direct interfaces
        for (Class<?> iface : clazz.getInterfaces()) {
            if (affectedClassNames.contains(iface.getName())) {
                return true;
            }
        }

        // Check superclass chain and their interfaces
        Class<?> current = clazz.getSuperclass();
        while (current != null && current != Object.class) {
            if (affectedClassNames.contains(current.getName())) {
                return true;
            }
            for (Class<?> iface : current.getInterfaces()) {
                if (affectedClassNames.contains(iface.getName())) {
                    return true;
                }
            }
            current = current.getSuperclass();
        }

        return false;
    }

    @Override
    public String getConfigFilePath() {
        return configManager.getConfigFilePath();
    }
    
    // ... rest of the file ...

    @Override
    public int getInstrumentationCount() {
        InstrumentationConfig config = configManager.getConfig();
        return config != null ? config.size() : 0;
    }

    @Override
    public void setDebugEnabled(boolean enabled) {
        Logger.info("Debug logging " + (enabled ? "enabled" : "disabled") + " via JMX");
        Logger.setDebugEnabled(enabled);
    }

    @Override
    public boolean isDebugEnabled() {
        return Logger.isDebugEnabled();
    }

    @Override
    public int getInstrumentedClassCount() {
        InstrumentationConfig config = configManager.getConfig();
        return config != null ? config.size() : 0;
    }

    /**
     * Shutdown the ConfigManager and unregister the MBean.
     */
    public static synchronized void shutdown() {
        if (instance != null) {
            instance.unregisterMBean();
            instance = null;
        }
    }
}
