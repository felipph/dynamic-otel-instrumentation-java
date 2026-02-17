package com.otel.sample.microservices.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Order {

    private String orderId;
    private String customerId;
    private String productId;
    private Integer quantity;
    private BigDecimal amount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private String processedBy;

    // Default constructor
    public Order() {
        this.orderId = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
    }

    // Full constructor
    public Order(String orderId, String customerId, String productId,
                 Integer quantity, BigDecimal amount, String status,
                 LocalDateTime createdAt, LocalDateTime processedAt, String processedBy) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
        this.processedBy = processedBy;
    }

    // Getters and Setters
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    public String getProcessedBy() { return processedBy; }
    public void setProcessedBy(String processedBy) { this.processedBy = processedBy; }

    // Builder
    public static OrderBuilder builder() {
        return new OrderBuilder();
    }

    public static class OrderBuilder {
        private String orderId;
        private String customerId;
        private String productId;
        private Integer quantity;
        private BigDecimal amount;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime processedAt;
        private String processedBy;

        public OrderBuilder orderId(String orderId) { this.orderId = orderId; return this; }
        public OrderBuilder customerId(String customerId) { this.customerId = customerId; return this; }
        public OrderBuilder productId(String productId) { this.productId = productId; return this; }
        public OrderBuilder quantity(Integer quantity) { this.quantity = quantity; return this; }
        public OrderBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public OrderBuilder status(String status) { this.status = status; return this; }
        public OrderBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public OrderBuilder processedAt(LocalDateTime processedAt) { this.processedAt = processedAt; return this; }
        public OrderBuilder processedBy(String processedBy) { this.processedBy = processedBy; return this; }

        public Order build() {
            return new Order(orderId, customerId, productId, quantity, amount, status, createdAt, processedAt, processedBy);
        }
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", customerId='" + customerId + '\'' +
                ", productId='" + productId + '\'' +
                ", quantity=" + quantity +
                ", amount=" + amount +
                ", status='" + status + '\'' +
                '}';
    }
}
