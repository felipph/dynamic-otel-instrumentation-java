package com.otel.dynamic.config.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for instrumenting a specific method.
 *
 * Contains the class name, method name, and a list of attribute extraction rules.
 */
public class MethodConfig {

    private String className;

    private String methodName;

    private List<AttributeDefinition> attributes;

    /**
     * Default constructor for JSON deserialization
     */
    public MethodConfig() {
        this.attributes = new ArrayList<>();
    }

    /**
     * Constructor with required fields
     */
    public MethodConfig(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
        this.attributes = new ArrayList<>();
    }

    /**
     * Constructor with all fields
     */
    public MethodConfig(String className, String methodName, List<AttributeDefinition> attributes) {
        this.className = className;
        this.methodName = methodName;
        this.attributes = attributes != null ? attributes : new ArrayList<>();
    }

    // Getters and Setters

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<AttributeDefinition> getAttributes() {
        return attributes;
    }

    public void setAttributes(List<AttributeDefinition> attributes) {
        this.attributes = attributes != null ? attributes : new ArrayList<>();
    }

    /**
     * Add an attribute definition to this method config
     */
    public void addAttribute(AttributeDefinition attribute) {
        if (this.attributes == null) {
            this.attributes = new ArrayList<>();
        }
        this.attributes.add(attribute);
    }

    @Override
    public String toString() {
        return "MethodConfig{" +
                "className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", attributes=" + attributes +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodConfig that = (MethodConfig) o;

        if (!Objects.equals(className, that.className)) return false;
        if (!Objects.equals(methodName, that.methodName)) return false;
        return Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
        result = 31 * result + (attributes != null ? attributes.hashCode() : 0);
        return result;
    }
}
