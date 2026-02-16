package com.otel.dynamic.jmx;

/**
 * JMX MBean interface for configuration management.
 *
 * Exposes operations for managing the instrumentation configuration at runtime.
 * Allows reloading configuration and querying the current state.
 */
public interface ConfigManagerMBean {

    /**
     * Reload the configuration from the configuration file.
     *
     * This will trigger a retransformation of classes if the configuration has changed.
     *
     * @return true if reload was successful, false otherwise
     */
    boolean reloadConfiguration();

    /**
     * Get the path to the current configuration file.
     *
     * @return the configuration file path
     */
    String getConfigFilePath();

    /**
     * Get the number of instrumentations currently configured.
     *
     * @return the count of instrumentation rules
     */
    int getInstrumentationCount();

    /**
     * Enable or disable debug logging.
     *
     * @param enabled true to enable debug logging, false to disable
     */
    void setDebugEnabled(boolean enabled);

    /**
     * Check if debug logging is enabled.
     *
     * @return true if debug logging is enabled
     */
    boolean isDebugEnabled();

    /**
     * Get the number of classes that have been instrumented.
     *
     * @return the count of instrumented classes
     */
    int getInstrumentedClassCount();
}
