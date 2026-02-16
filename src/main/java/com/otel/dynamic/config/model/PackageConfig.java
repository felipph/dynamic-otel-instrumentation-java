package com.otel.dynamic.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for instrumenting all classes and methods in a given package.
 *
 * When specified, every public method of every class in the package will be
 * instrumented with span creation. If {@code recursive} is true, sub-packages
 * are also included. If {@code annotations} is specified, only classes annotated
 * with at least one of the listed annotations will be instrumented.
 */
public class PackageConfig {

    @JsonProperty("packageName")
    private String packageName;

    @JsonProperty("recursive")
    private boolean recursive;

    @JsonProperty("annotations")
    private List<String> annotations;

    /**
     * Default constructor for JSON deserialization
     */
    public PackageConfig() {
        this.annotations = new ArrayList<>();
    }

    /**
     * Constructor with all fields
     */
    public PackageConfig(String packageName, boolean recursive, List<String> annotations) {
        this.packageName = packageName;
        this.recursive = recursive;
        this.annotations = annotations != null ? annotations : new ArrayList<>();
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations != null ? annotations : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "PackageConfig{" +
                "packageName='" + packageName + '\'' +
                ", recursive=" + recursive +
                ", annotations=" + annotations +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PackageConfig that = (PackageConfig) o;

        if (recursive != that.recursive) return false;
        return Objects.equals(packageName, that.packageName);
    }

    @Override
    public int hashCode() {
        int result = packageName != null ? packageName.hashCode() : 0;
        result = 31 * result + (recursive ? 1 : 0);
        return result;
    }
}
