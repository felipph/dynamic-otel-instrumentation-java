package com.otel.sample.webmvc.service;

import com.otel.sample.webmvc.dto.OrderDTO;
import com.otel.sample.webmvc.entity.Order;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Order operations.
 * This interface will be instrumented - all implementations will be automatically traced.
 */
public interface OrderService {

    Order createOrder(OrderDTO orderDTO);

    Optional<Order> getOrderById(Long id);

    Optional<Order> getOrderByNumber(String orderNumber);

    List<Order> getOrdersByCustomerId(Long customerId);

    List<Order> getAllOrders();

    Order processOrder(Long orderId);

    Order updateOrderStatus(Long orderId, Order.OrderStatus status);

    void cancelOrder(Long orderId);
}
