package com.otel.sample.microservices.controller;

import com.otel.sample.microservices.dto.OrderRequest;
import com.otel.sample.microservices.model.Order;
import com.otel.sample.microservices.model.OrderResult;
import com.otel.sample.microservices.service.OrderService;
import com.otel.sample.microservices.service.ProcessingStats;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResult> createOrder(@Valid @RequestBody OrderRequest request) {
        log.info("REST request to create order - Customer: {} - Product: {}",
                request.getCustomerId(), request.getProductId());

        Order order = orderService.createOrder(request);

        OrderResult result = new OrderResult(
                order.getOrderId(),
                order.getStatus(),
                "Order created and sent to processing queue",
                null
        );

        return ResponseEntity.created(URI.create("/api/orders/" + order.getOrderId()))
                .body(result);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        log.info("REST request to get order: {}", orderId);
        return orderService.getOrderById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        log.info("REST request to get all orders");
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<Order>> getOrdersByCustomer(@PathVariable String customerId) {
        log.info("REST request to get orders for customer: {}", customerId);
        List<Order> orders = orderService.getOrdersByCustomer(customerId);
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/stats")
    public ResponseEntity<ProcessingStats> getStats() {
        log.info("REST request to get processing stats");
        return ResponseEntity.ok(orderService.getStats());
    }
}
