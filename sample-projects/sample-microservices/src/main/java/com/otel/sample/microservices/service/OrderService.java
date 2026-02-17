package com.otel.sample.microservices.service;

import com.otel.sample.microservices.dto.OrderRequest;
import com.otel.sample.microservices.model.Order;

import java.util.List;
import java.util.Optional;

public interface OrderService {

    /**
     * Creates an order from the request and sends it to the message queue.
     */
    Order createOrder(OrderRequest request);

    /**
     * Processes an order received from the message queue.
     */
    Order processOrder(Order order);

    /**
     * Gets an order by ID.
     */
    Optional<Order> getOrderById(String orderId);

    /**
     * Gets all orders.
     */
    List<Order> getAllOrders();

    /**
     * Gets orders by customer ID.
     */
    List<Order> getOrdersByCustomer(String customerId);

    /**
     * Gets processing statistics.
     */
    ProcessingStats getStats();
}
