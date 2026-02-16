package com.otel.dynamic.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Definition for extracting an attribute from a method argument.
 *
 * Specifies which argument to extract from (argIndex), which method to call
 * on that argument (methodCall), and what to name the resulting attribute (attributeName).
 */
public class AttributeDefinition {

    @JsonProperty("argIndex")
    private int argIndex;

    @JsonProperty("methodCall")
    private String methodCall;

    @JsonProperty("attributeName")
    private String attributeName;

    /**
     * Default constructor for JSON deserialization
     */
    public AttributeDefinition() {
    }

    /**
     * Constructor with all fields
     */
    public AttributeDefinition(int argIndex, String methodCall, String attributeName) {
        this.argIndex = argIndex;
        this.methodCall = methodCall;
        this.attributeName = attributeName;
    }

    // Getters and Setters

    public int getArgIndex() {
        return argIndex;
    }

    public void setArgIndex(int argIndex) {
        this.argIndex = argIndex;
    }

    public String getMethodCall() {
        return methodCall;
    }

    public void setMethodCall(String methodCall) {
        this.methodCall = methodCall;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    @Override
    public String toString() {
        return "AttributeDefinition{" +
                "argIndex=" + argIndex +
                ", methodCall='" + methodCall + '\'' +
                ", attributeName='" + attributeName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        AttributeDefinition that = (AttributeDefinition) o;

        if (argIndex != that.argIndex) return false;
        if (methodCall != null ? !methodCall.equals(that.methodCall) : that.methodCall != null) return false;
        return attributeName != null ? attributeName.equals(that.attributeName) : that.attributeName == null;
    }

    @Override
    public int hashCode() {
        int result = argIndex;
        result = 31 * result + (methodCall != null ? methodCall.hashCode() : 0);
        result = 31 * result + (attributeName != null ? attributeName.hashCode() : 0);
        return result;
    }
}
