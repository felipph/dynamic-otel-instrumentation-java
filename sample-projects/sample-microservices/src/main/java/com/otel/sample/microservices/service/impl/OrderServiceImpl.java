package com.otel.sample.microservices.service.impl;

import com.otel.sample.microservices.dto.OrderRequest;
import com.otel.sample.microservices.messaging.OrderMessageProducer;
import com.otel.sample.microservices.model.Order;
import com.otel.sample.microservices.repository.OrderRepository;
import com.otel.sample.microservices.repository.ProductRepository;
import com.otel.sample.microservices.service.OrderService;
import com.otel.sample.microservices.service.ProcessingStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final OrderMessageProducer messageProducer;

    @Value("${spring.application.name:sample-microservices}")
    private String applicationName;

    public OrderServiceImpl(OrderRepository orderRepository,
                            ProductRepository productRepository,
                            OrderMessageProducer messageProducer) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.messageProducer = messageProducer;
    }

    @Override
    public Order createOrder(OrderRequest request) {
        log.info("Creating order for customer: {} - Product: {}", request.getCustomerId(), request.getProductId());

        // Validate product exists
        if (!productRepository.existsById(request.getProductId())) {
            throw new IllegalArgumentException("Product not found: " + request.getProductId());
        }

        // Calculate total amount
        BigDecimal unitPrice = request.getUnitPrice();
        if (unitPrice == null) {
            unitPrice = productRepository.findById(request.getProductId())
                    .map(ProductRepository.ProductInfo::getPrice)
                    .orElse(BigDecimal.ZERO);
        }
        BigDecimal totalAmount = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        // Create order
        Order order = new Order();
        order.setCustomerId(request.getCustomerId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setAmount(totalAmount);
        order.setStatus("CREATED");

        // Save initial order
        Order savedOrder = orderRepository.save(order);
        log.info("Order created: {} - Amount: {}", savedOrder.getOrderId(), savedOrder.getAmount());

        // Send to message queue for async processing
        messageProducer.sendOrder(savedOrder);
        log.info("Order sent to queue: {}", savedOrder.getOrderId());

        return savedOrder;
    }

    @Override
    public Order processOrder(Order order) {
        log.info("Processing order: {} - Instance: {}", order.getOrderId(), applicationName);

        // Update status to processing
        order.setStatus("PROCESSING");
        orderRepository.save(order);

        // Simulate processing logic
        try {
            // Simulate some processing time
            Thread.sleep(100);

            // Simulate validation
            if (order.getAmount().compareTo(BigDecimal.valueOf(10000)) > 0) {
                throw new IllegalStateException("Order amount exceeds limit");
            }

            // Mark as processed
            order.setStatus("PROCESSED");
            order.setProcessedAt(LocalDateTime.now());
            order.setProcessedBy(applicationName);

            Order processedOrder = orderRepository.save(order);
            log.info("Order processed successfully: {}", order.getOrderId());

            return processedOrder;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            order.setStatus("FAILED");
            orderRepository.save(order);
            throw new RuntimeException("Order processing interrupted", e);
        } catch (Exception e) {
            order.setStatus("FAILED");
            order.setProcessedAt(LocalDateTime.now());
            order.setProcessedBy(applicationName);
            orderRepository.save(order);
            log.error("Order processing failed: {}", order.getOrderId(), e);
            throw e;
        }
    }

    @Override
    public Optional<Order> getOrderById(String orderId) {
        log.info("Fetching order: {}", orderId);
        return orderRepository.findById(orderId);
    }

    @Override
    public List<Order> getAllOrders() {
        log.info("Fetching all orders");
        return orderRepository.findAll();
    }

    @Override
    public List<Order> getOrdersByCustomer(String customerId) {
        log.info("Fetching orders for customer: {}", customerId);
        return orderRepository.findByCustomerId(customerId);
    }

    @Override
    public ProcessingStats getStats() {
        return new ProcessingStats(
                orderRepository.count(),
                orderRepository.countByStatus("CREATED") + orderRepository.countByStatus("PENDING"),
                orderRepository.countByStatus("PROCESSED"),
                orderRepository.countByStatus("FAILED")
        );
    }
}
