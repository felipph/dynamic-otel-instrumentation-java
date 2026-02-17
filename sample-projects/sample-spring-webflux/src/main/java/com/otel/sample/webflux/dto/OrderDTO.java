package com.otel.sample.webflux.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class OrderDTO {
    private Long id;
    private String orderNumber;
    private Long customerId;
    private String status;
    private BigDecimal total;

    @NotNull(message = "Items are required")
    private List<OrderItemDTO> items = new ArrayList<>();

    public OrderDTO() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public List<OrderItemDTO> getItems() { return items; }
    public void setItems(List<OrderItemDTO> items) { this.items = items; }

    public static class OrderItemDTO {
        private String productName;
        private String sku;
        private Integer quantity;
        private BigDecimal unitPrice;

        public OrderItemDTO() {}

        public String getProductName() { return productName; }
        public void setProductName(String productName) { this.productName = productName; }

        public String getSku() { return sku; }
        public void setSku(String sku) { this.sku = sku; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public BigDecimal getUnitPrice() { return unitPrice; }
        public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    }
}
