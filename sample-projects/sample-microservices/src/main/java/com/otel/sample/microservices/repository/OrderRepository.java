package com.otel.sample.microservices.repository;

import com.otel.sample.microservices.model.Order;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dummy in-memory order repository.
 * In a real application, this would use a database.
 */
@org.springframework.stereotype.Repository
public class OrderRepository {

    private final Map<String, Order> orders = new ConcurrentHashMap<>();

    public Order save(Order order) {
        orders.put(order.getOrderId(), order);
        return order;
    }

    public Optional<Order> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    public List<Order> findAll() {
        return new ArrayList<>(orders.values());
    }

    public List<Order> findByStatus(String status) {
        return orders.values().stream()
                .filter(o -> status.equals(o.getStatus()))
                .toList();
    }

    public List<Order> findByCustomerId(String customerId) {
        return orders.values().stream()
                .filter(o -> customerId.equals(o.getCustomerId()))
                .toList();
    }

    public void deleteById(String orderId) {
        orders.remove(orderId);
    }

    public long count() {
        return orders.size();
    }

    public long countByStatus(String status) {
        return orders.values().stream()
                .filter(o -> status.equals(o.getStatus()))
                .count();
    }
}
