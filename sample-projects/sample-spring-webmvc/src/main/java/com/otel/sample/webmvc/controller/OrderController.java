package com.otel.sample.webmvc.controller;

import com.otel.sample.webmvc.dto.OrderDTO;
import com.otel.sample.webmvc.entity.Order;
import com.otel.sample.webmvc.service.OrderService;
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
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderDTO orderDTO) {
        log.info("REST request to create order for customer: {}", orderDTO.getCustomerId());
        Order order = orderService.createOrder(orderDTO);
        return ResponseEntity.created(URI.create("/api/orders/" + order.getId()))
                .body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        log.info("REST request to get order: {}", id);
        return orderService.getOrderById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<Order> getOrderByNumber(@PathVariable String orderNumber) {
        log.info("REST request to get order by number: {}", orderNumber);
        return orderService.getOrderByNumber(orderNumber)
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
    public ResponseEntity<List<Order>> getOrdersByCustomer(@PathVariable Long customerId) {
        log.info("REST request to get orders for customer: {}", customerId);
        List<Order> orders = orderService.getOrdersByCustomerId(customerId);
        return ResponseEntity.ok(orders);
    }

    @PostMapping("/{id}/process")
    public ResponseEntity<Order> processOrder(@PathVariable Long id) {
        log.info("REST request to process order: {}", id);
        Order order = orderService.processOrder(id);
        return ResponseEntity.ok(order);
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Order> updateOrderStatus(@PathVariable Long id,
                                                    @RequestParam Order.OrderStatus status) {
        log.info("REST request to update order {} status to: {}", id, status);
        Order order = orderService.updateOrderStatus(id, status);
        return ResponseEntity.ok(order);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelOrder(@PathVariable Long id) {
        log.info("REST request to cancel order: {}", id);
        orderService.cancelOrder(id);
        return ResponseEntity.noContent().build();
    }
}
