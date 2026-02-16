package com.otel.dynamic.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Static registry that holds attribute extraction rules for instrumented methods.
 *
 * Uses System properties as the backing store so that data is shared across all
 * classloaders in the JVM. This solves the classloader isolation problem where
 * the agent classloader (which populates the config) and the app classloader
 * (where inlined advice runs) would otherwise see different static fields.
 *
 * Serialization format per system property:
 *   Key:   "otel.dynamic.rules.{className}#{methodName}"
 *   Value: "argIndex|methodCall|attributeName;argIndex|methodCall|attributeName;..."
 *
 * This class is intentionally free of any external dependencies (no Jackson, no
 * ConfigurationManager) because it gets injected into the application classloader
 * as a helper class and is accessed by inlined ByteBuddy advice code.
 */
public class DynamicInstrumentationConfig {

    private static final String PROP_PREFIX = "otel.dynamic.rules.";

    /**
     * Simple POJO representing a single attribute extraction rule.
     * No external dependencies — safe for use in inlined advice.
     */
    public static class AttributeRule {
        private final int argIndex;
        private final String methodCall;
        private final String attributeName;

        public AttributeRule(int argIndex, String methodCall, String attributeName) {
            this.argIndex = argIndex;
            this.methodCall = methodCall;
            this.attributeName = attributeName;
        }

        public int getArgIndex() {
            return argIndex;
        }

        public String getMethodCall() {
            return methodCall;
        }

        public String getAttributeName() {
            return attributeName;
        }
    }

    /**
     * Register attribute extraction rules for a specific class+method pair.
     * Serializes the rules into a system property for cross-classloader access.
     *
     * @param className  fully qualified class name (dot-separated)
     * @param methodName method name
     * @param rules      list of attribute extraction rules
     */
    public static void register(String className, String methodName, List<AttributeRule> rules) {
        if (rules != null && !rules.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < rules.size(); i++) {
                if (i > 0) {
                    sb.append(';');
                }
                AttributeRule r = rules.get(i);
                sb.append(r.getArgIndex())
                  .append('|')
                  .append(r.getMethodCall() != null ? r.getMethodCall() : "")
                  .append('|')
                  .append(r.getAttributeName());
            }
            System.setProperty(PROP_PREFIX + className + "#" + methodName, sb.toString());
        }
    }

    /**
     * Look up attribute extraction rules for a given class+method.
     * Deserializes from the system property on each call.
     *
     * @param className  fully qualified class name (dot-separated)
     * @param methodName method name
     * @return list of rules, or null if none configured
     */
    public static List<AttributeRule> getRules(String className, String methodName) {
        String value = System.getProperty(PROP_PREFIX + className + "#" + methodName);
        if (value == null || value.isEmpty()) {
            return null;
        }
        List<AttributeRule> rules = new ArrayList<>();
        String[] entries = value.split(";");
        for (String entry : entries) {
            String[] parts = entry.split("\\|", -1);
            if (parts.length == 3) {
                int argIndex = Integer.parseInt(parts[0]);
                String methodCall = parts[1].isEmpty() ? null : parts[1];
                String attributeName = parts[2];
                rules.add(new AttributeRule(argIndex, methodCall, attributeName));
            }
        }
        return rules.isEmpty() ? null : rules;
    }

    /**
     * Result of a hierarchy-aware rule lookup.
     * Carries both the rules and the class name where they were found,
     * so callers can determine if the match came from an interface or superclass.
     */
    public static class RuleMatch {
        private final String sourceClassName;
        private final List<AttributeRule> rules;
        private final boolean fromInterface;

        public RuleMatch(String sourceClassName, List<AttributeRule> rules, boolean fromInterface) {
            this.sourceClassName = sourceClassName;
            this.rules = rules;
            this.fromInterface = fromInterface;
        }

        public String getSourceClassName() {
            return sourceClassName;
        }

        public List<AttributeRule> getRules() {
            return rules;
        }

        public boolean isFromInterface() {
            return fromInterface;
        }
    }

    /**
     * Look up attribute extraction rules by walking the class hierarchy.
     * First tries the exact runtime class name, then checks all interfaces
     * and superclasses. This allows rules registered under an interface name
     * to be found when the advice fires on an implementing class.
     *
     * @param runtimeClassName the actual class name at runtime (dot-separated)
     * @param methodName       method name
     * @return list of rules, or null if none configured
     */
    public static List<AttributeRule> getRulesForHierarchy(String runtimeClassName, String methodName) {
        RuleMatch match = findRulesForHierarchy(runtimeClassName, methodName);
        return match != null ? match.getRules() : null;
    }

    /**
     * Look up attribute extraction rules by walking the class hierarchy,
     * returning a {@link RuleMatch} that includes the source class name
     * and whether the match came from an interface.
     *
     * @param runtimeClassName the actual class name at runtime (dot-separated)
     * @param methodName       method name
     * @return a RuleMatch with rules and source info, or null if none configured
     */
    public static RuleMatch findRulesForHierarchy(String runtimeClassName, String methodName) {
        // Fast path: try exact class name first
        List<AttributeRule> rules = getRules(runtimeClassName, methodName);
        if (rules != null) {
            return new RuleMatch(runtimeClassName, rules, false);
        }

        // Walk the class hierarchy: interfaces and superclasses
        try {
            Class<?> clazz = Class.forName(runtimeClassName, false,
                    Thread.currentThread().getContextClassLoader());

            // Check all interfaces (including inherited ones)
            for (Class<?> iface : clazz.getInterfaces()) {
                rules = getRules(iface.getName(), methodName);
                if (rules != null) {
                    return new RuleMatch(iface.getName(), rules, true);
                }
            }

            // Walk superclass chain
            Class<?> superClass = clazz.getSuperclass();
            while (superClass != null && superClass != Object.class) {
                rules = getRules(superClass.getName(), methodName);
                if (rules != null) {
                    return new RuleMatch(superClass.getName(), rules, false);
                }
                // Also check interfaces of the superclass
                for (Class<?> iface : superClass.getInterfaces()) {
                    rules = getRules(iface.getName(), methodName);
                    if (rules != null) {
                        return new RuleMatch(iface.getName(), rules, true);
                    }
                }
                superClass = superClass.getSuperclass();
            }
        } catch (ClassNotFoundException ignored) {
            // If we can't load the class, fall through to null
        }

        return null;
    }

    /**
     * Clear all registered rules (useful for hot-reload).
     */
    public static void clear() {
        List<String> toRemove = new ArrayList<>();
        for (String key : System.getProperties().stringPropertyNames()) {
            if (key.startsWith(PROP_PREFIX)) {
                toRemove.add(key);
            }
        }
        for (String key : toRemove) {
            System.clearProperty(key);
        }
    }
}
