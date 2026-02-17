package com.otel.dynamic.util;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class for safe reflection operations.
 *
 * Provides cached method lookup and safe invocation with proper error handling.
 * Used by UniversalAdvice to extract attributes from method arguments without
 * referencing application classes directly (to avoid ClassLoader isolation issues).
 */
public class ReflectionHelper {

    // Cache for Method objects to avoid repeated lookup overhead
    private static final ConcurrentHashMap<MethodKey, Method> methodCache = new ConcurrentHashMap<>();

    /**
     * Invoke a method on a target object safely.
     *
     * This method handles all reflection exceptions and returns an Optional
     * that is empty if the invocation fails for any reason.
     *
     * @param target the object to invoke the method on
     * @param methodName the name of the method to invoke
     * @return an Optional containing the result, or empty if invocation failed
     */
    public static Optional<Object> invokeMethodSafely(Object target, String methodName) {
        if (target == null || methodName == null || methodName.isEmpty()) {
            Logger.debug("Invalid invocation: target=" + target + ", methodName=" + methodName);
            return Optional.empty();
        }

        try {
            Class<?> targetClass = target.getClass();
            MethodKey key = new MethodKey(targetClass, methodName);

            // Get method from cache or look it up
            Method method = methodCache.computeIfAbsent(key, k -> findMethod(targetClass, methodName));

            if (method == null) {
                Logger.debug("Method not found: " + methodName + " on class: " + targetClass.getName());
                return Optional.empty();
            }

            // Invoke the method
            Object result = method.invoke(target);

            // Handle null result vs empty optional
            return Optional.ofNullable(result);

        } catch (IllegalAccessException e) {
            Logger.debug("Method not accessible: " + methodName + " on class: " + target.getClass().getName(), e);
            return Optional.empty();
        } catch (IllegalArgumentException e) {
            Logger.debug("Invalid arguments for method: " + methodName, e);
            return Optional.empty();
        } catch (InvocationTargetException e) {
            Logger.debug("Method threw exception: " + methodName, e.getCause());
            return Optional.empty();
        } catch (Exception e) {
            Logger.debug("Unexpected error invoking method: " + methodName, e);
            return Optional.empty();
        }
    }

    /**
     * Find a no-arg method by name on the given class.
     *
     * Searches public methods only, as we're calling simple getters like
     * getBatchId(), getRootId(), toString(), etc.
     *
     * @param clazz the class to search
     * @param methodName the name of the method
     * @return the Method object, or null if not found
     */
    private static Method findMethod(Class<?> clazz, String methodName) {
        try {
            return clazz.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            // Method not found with no args
            Logger.debug("No-arg method not found: " + methodName + " on class: " + clazz.getName());
            return null;
        }
    }

    /**
     * Clear the method cache.
     *
     * Useful for testing or if you want to free memory.
     */
    public static void clearCache() {
        methodCache.clear();
    }

    /**
     * Get the current size of the method cache.
     */
    public static int getCacheSize() {
        return methodCache.size();
    }

    /**
     * Key class for caching Method objects.
     * Combines class and method name for unique identification.
     */
    private static class MethodKey {
        private final Class<?> clazz;
        private final String methodName;

        public MethodKey(Class<?> clazz, String methodName) {
            this.clazz = clazz;
            this.methodName = methodName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MethodKey methodKey = (MethodKey) o;

            if (!clazz.equals(methodKey.clazz)) return false;
            return methodName.equals(methodKey.methodName);
        }

        @Override
        public int hashCode() {
            int result = clazz.hashCode();
            result = 31 * result + methodName.hashCode();
            return result;
        }
    }

    /**
     * Get the class name of an object (handles null safely).
     *
     * @param obj the object
     * @return the class name, or "null" if obj is null
     */
    public static String getClassName(Object obj) {
        return obj != null ? obj.getClass().getName() : "null";
    }

    /**
     * Convert an object to String safely.
     *
     * @param obj the object to convert
     * @return the string representation, or "null" if obj is null
     */
    public static String safeToString(Object obj) {
        return obj != null ? obj.toString() : "null";
    }

    /**
     * Invoke a chain of methods on a target object safely.
     *
     * Supports syntax like "getCustomer.getAddress.getCity" where each method
     * in the chain is called on the result of the previous method.
     *
     * @param target the object to start the chain on
     * @param methodChain dot-separated method names (e.g., "getCustomer.getAddress.getCity")
     * @return an Optional containing the final result, or empty if any step failed
     */
    public static Optional<Object> invokeMethodChain(Object target, String methodChain) {
        if (target == null || methodChain == null || methodChain.isEmpty()) {
            Logger.debug("Invalid chain invocation: target=" + target + ", methodChain=" + methodChain);
            return Optional.empty();
        }

        Object current = target;
        for (String methodName : methodChain.split("\\.")) {
            if (current == null) {
                Logger.debug("Chain terminated early: null result for method in chain: " + methodChain);
                return Optional.empty();
            }
            Optional<Object> result = invokeMethodSafely(current, methodName.trim());
            if (!result.isPresent()) {
                Logger.debug("Chain invocation failed at method: " + methodName.trim() + " on class: " + current.getClass().getName());
                return Optional.empty();
            }
            current = result.get();
        }

        return Optional.ofNullable(current);
    }
}
