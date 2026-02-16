package com.otel.dynamic.util;

/**
 * Agent-specific logging utility.
 *
 * Provides simple logging methods for the instrumentation agent.
 * Uses System.out by default but could be configured to use a proper logging framework.
 */
public class Logger {

    private static final String PREFIX = "[DynamicInstrumentation] ";
    private static boolean debugEnabled = false;

    /**
     * Enable or disable debug logging
     */
    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /**
     * Check if debug logging is enabled
     */
    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    /**
     * Log an informational message
     */
    public static void info(String message) {
        System.out.println(PREFIX + "[INFO] " + message);
    }

    /**
     * Log a warning message
     */
    public static void warn(String message) {
        System.err.println(PREFIX + "[WARN] " + message);
    }

    /**
     * Log an error message
     */
    public static void error(String message) {
        System.err.println(PREFIX + "[ERROR] " + message);
    }

    /**
     * Log an error message with exception
     */
    public static void error(String message, Throwable t) {
        System.err.println(PREFIX + "[ERROR] " + message);
        if (t != null) {
            t.printStackTrace(System.err);
        }
    }

    /**
     * Log a debug message (only if debug is enabled)
     */
    public static void debug(String message) {
        if (debugEnabled) {
            System.out.println(PREFIX + "[DEBUG] " + message);
        }
    }

    /**
     * Log a debug message with exception (only if debug is enabled)
     */
    public static void debug(String message, Throwable t) {
        if (debugEnabled) {
            System.out.println(PREFIX + "[DEBUG] " + message);
            if (t != null) {
                t.printStackTrace(System.out);
            }
        }
    }
}
