package com.otel.dynamic.config.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Configuration for extracting attributes from a method's return value.
 *
 * Used to capture data from the object returned by an instrumented method
 * and add it as span attributes.
 */
public class ReturnValueAttribute {

    @JsonProperty("methodCall")
    private String methodCall;

    @JsonProperty("attributeName")
    private String attributeName;

    /**
     * Default constructor for JSON deserialization
     */
    public ReturnValueAttribute() {
    }

    /**
     * Constructor with all fields
     *
     * @param methodCall    the method(s) to invoke on the return value (supports chaining with dots)
     * @param attributeName the name of the span attribute to set
     */
    public ReturnValueAttribute(String methodCall, String attributeName) {
        this.methodCall = methodCall;
        this.attributeName = attributeName;
    }

    // Getters and Setters

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
        return "ReturnValueAttribute{" +
                "methodCall='" + methodCall + '\'' +
                ", attributeName='" + attributeName + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReturnValueAttribute that = (ReturnValueAttribute) o;

        if (!Objects.equals(methodCall, that.methodCall)) return false;
        return Objects.equals(attributeName, that.attributeName);
    }

    @Override
    public int hashCode() {
        int result = methodCall != null ? methodCall.hashCode() : 0;
        result = 31 * result + (attributeName != null ? attributeName.hashCode() : 0);
        return result;
    }
}
