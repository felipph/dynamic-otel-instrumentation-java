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

    private List<ReturnValueAttribute> returnValueAttributes;

    /**
     * Default constructor for JSON deserialization
     */
    public MethodConfig() {
        this.attributes = new ArrayList<>();
        this.returnValueAttributes = new ArrayList<>();
    }

    /**
     * Constructor with required fields
     */
    public MethodConfig(String className, String methodName) {
        this.className = className;
        this.methodName = methodName;
        this.attributes = new ArrayList<>();
        this.returnValueAttributes = new ArrayList<>();
    }

    /**
     * Constructor with all fields
     */
    public MethodConfig(String className, String methodName, List<AttributeDefinition> attributes) {
        this.className = className;
        this.methodName = methodName;
        this.attributes = attributes != null ? attributes : new ArrayList<>();
        this.returnValueAttributes = new ArrayList<>();
    }

    /**
     * Constructor with all fields including return value attributes
     */
    public MethodConfig(String className, String methodName, List<AttributeDefinition> attributes,
                        List<ReturnValueAttribute> returnValueAttributes) {
        this.className = className;
        this.methodName = methodName;
        this.attributes = attributes != null ? attributes : new ArrayList<>();
        this.returnValueAttributes = returnValueAttributes != null ? returnValueAttributes : new ArrayList<>();
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

    public List<ReturnValueAttribute> getReturnValueAttributes() {
        return returnValueAttributes;
    }

    public void setReturnValueAttributes(List<ReturnValueAttribute> returnValueAttributes) {
        this.returnValueAttributes = returnValueAttributes != null ? returnValueAttributes : new ArrayList<>();
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

    /**
     * Add a return value attribute definition to this method config
     */
    public void addReturnValueAttribute(ReturnValueAttribute returnValueAttribute) {
        if (this.returnValueAttributes == null) {
            this.returnValueAttributes = new ArrayList<>();
        }
        this.returnValueAttributes.add(returnValueAttribute);
    }

    @Override
    public String toString() {
        return "MethodConfig{" +
                "className='" + className + '\'' +
                ", methodName='" + methodName + '\'' +
                ", attributes=" + attributes +
                ", returnValueAttributes=" + returnValueAttributes +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MethodConfig that = (MethodConfig) o;

        if (!Objects.equals(className, that.className)) return false;
        if (!Objects.equals(methodName, that.methodName)) return false;
        if (!Objects.equals(attributes, that.attributes)) return false;
        return Objects.equals(returnValueAttributes, that.returnValueAttributes);
    }

    @Override
    public int hashCode() {
        int result = className != null ? className.hashCode() : 0;
        result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
        result = 31 * result + (attributes != null ? attributes.hashCode() : 0);
        result = 31 * result + (returnValueAttributes != null ? returnValueAttributes.hashCode() : 0);
        return result;
    }
}
