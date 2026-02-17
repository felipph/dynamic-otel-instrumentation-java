package com.otel.sample.microservices.model;

import java.time.LocalDateTime;

public class OrderResult {

    private String orderId;
    private String status;
    private String message;
    private String processedBy;
    private LocalDateTime timestamp;

    public OrderResult() {
        this.timestamp = LocalDateTime.now();
    }

    public OrderResult(String orderId, String status, String message, String processedBy) {
        this.orderId = orderId;
        this.status = status;
        this.message = message;
        this.processedBy = processedBy;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    @Override
    public String toString() {
        return "OrderResult{" +
                "orderId='" + orderId + '\'' +
                ", status='" + status + '\'' +
                ", message='" + message + '\'' +
                ", processedBy='" + processedBy + '\'' +
                '}';
    }
}
