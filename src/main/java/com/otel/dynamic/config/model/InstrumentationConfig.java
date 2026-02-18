package com.otel.dynamic.config.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Top-level configuration object for instrumentation rules.
 *
 * Contains a list of method configurations to be applied.
 * This is the root object when deserializing instrumentation.json.
 */
public class InstrumentationConfig {

    private List<MethodConfig> instrumentations;

    private List<PackageConfig> packages;

    /**
     * Global setting for concrete-only instrumentation.
     * When true, only concrete (non-abstract) classes will be instrumented
     * for interface-based configurations. Can be overridden at the method level.
     */
    private Boolean concreteOnly;

    /**
     * Default constructor for JSON deserialization
     */
    public InstrumentationConfig() {
        this.instrumentations = new ArrayList<>();
        this.packages = new ArrayList<>();
    }

    /**
     * Constructor with instrumentation list
     */
    public InstrumentationConfig(List<MethodConfig> instrumentations) {
        this.instrumentations = instrumentations != null ? instrumentations : new ArrayList<>();
        this.packages = new ArrayList<>();
    }

    // Getters and Setters

    public List<MethodConfig> getInstrumentations() {
        return instrumentations;
    }

    public void setInstrumentations(List<MethodConfig> instrumentations) {
        this.instrumentations = instrumentations != null ? instrumentations : new ArrayList<>();
    }

    /**
     * Add a method configuration to this config
     */
    public void addInstrumentation(MethodConfig config) {
        if (this.instrumentations == null) {
            this.instrumentations = new ArrayList<>();
        }
        this.instrumentations.add(config);
    }

    public List<PackageConfig> getPackages() {
        return packages;
    }

    public void setPackages(List<PackageConfig> packages) {
        this.packages = packages != null ? packages : new ArrayList<>();
    }

    public Boolean getConcreteOnly() {
        return concreteOnly;
    }

    public void setConcreteOnly(Boolean concreteOnly) {
        this.concreteOnly = concreteOnly;
    }

    /**
     * Check if this configuration is empty (no instrumentations defined)
     */
    public boolean isEmpty() {
        return (instrumentations == null || instrumentations.isEmpty())
                && (packages == null || packages.isEmpty());
    }

    /**
     * Get the number of instrumentations defined
     */
    public int size() {
        return instrumentations != null ? instrumentations.size() : 0;
    }

    @Override
    public String toString() {
        return "InstrumentationConfig{" +
                "instrumentations=" + instrumentations +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        InstrumentationConfig that = (InstrumentationConfig) o;

        return Objects.equals(instrumentations, that.instrumentations);
    }

    @Override
    public int hashCode() {
        return instrumentations != null ? instrumentations.hashCode() : 0;
    }
}
