package com.otel.dynamic.agent;

import java.lang.instrument.Instrumentation;

/**
 * Accessor for the JVM Instrumentation instance.
 */
public class InstrumentationAccessor {
    private static volatile Instrumentation instrumentation;

    public static void setInstrumentation(Instrumentation inst) {
        instrumentation = inst;
    }

    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
}
