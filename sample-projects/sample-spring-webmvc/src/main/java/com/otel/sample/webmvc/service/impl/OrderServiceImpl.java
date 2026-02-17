package com.otel.sample.webmvc.service.impl;

import com.otel.sample.webmvc.dto.OrderDTO;
import com.otel.sample.webmvc.entity.Customer;
import com.otel.sample.webmvc.entity.Order;
import com.otel.sample.webmvc.entity.OrderItem;
import com.otel.sample.webmvc.repository.CustomerRepository;
import com.otel.sample.webmvc.repository.OrderRepository;
import com.otel.sample.webmvc.service.OrderService;
import com.otel.sample.webmvc.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentService paymentService;

    public OrderServiceImpl(OrderRepository orderRepository, CustomerRepository customerRepository, PaymentService paymentService) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.paymentService = paymentService;
    }

    @Override
    @Transactional
    public Order createOrder(OrderDTO orderDTO) {
        log.info("Creating order for customer: {}", orderDTO.getCustomerId());

        Customer customer = customerRepository.findById(orderDTO.getCustomerId())
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + orderDTO.getCustomerId()));

        BigDecimal subtotal = calculateSubtotal(orderDTO);
        BigDecimal shipping = paymentService.calculateShipping(
                customer.getAddress() != null ? customer.getAddress().getZipCode() : null,
                subtotal
        );
        BigDecimal tax = subtotal.multiply(BigDecimal.valueOf(0.10));
        BigDecimal total = subtotal.add(tax).add(shipping);

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomer(customer);
        order.setStatus(Order.OrderStatus.PENDING);
        order.setSubtotal(subtotal);
        order.setTax(tax);
        order.setShipping(shipping);
        order.setTotalAmount(total);

        // Add items
        if (orderDTO.getItems() != null) {
            orderDTO.getItems().forEach(item -> {
                OrderItem orderItem = new OrderItem();
                orderItem.setProductName(item.getProductName());
                orderItem.setSku(item.getSku());
                orderItem.setQuantity(item.getQuantity());
                orderItem.setUnitPrice(item.getUnitPrice());
                orderItem.setTotalPrice(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
                order.getItems().add(orderItem);
            });
        }

        Order savedOrder = orderRepository.save(order);

        // Process payment asynchronously
        paymentService.processPaymentAsync(savedOrder.getId(), total, "CREDIT_CARD");

        return savedOrder;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> getOrderById(Long id) {
        log.info("Fetching order by id: {}", id);
        return orderRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> getOrderByNumber(String orderNumber) {
        log.info("Fetching order by number: {}", orderNumber);
        return orderRepository.findByOrderNumber(orderNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getOrdersByCustomerId(Long customerId) {
        log.info("Fetching orders for customer: {}", customerId);
        return orderRepository.findByCustomerId(customerId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        log.info("Fetching all orders");
        return orderRepository.findAll();
    }

    @Override
    @Transactional
    public Order processOrder(Long orderId) {
        log.info("Processing order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() != Order.OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Order must be confirmed before processing");
        }

        order.setStatus(Order.OrderStatus.PROCESSING);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order updateOrderStatus(Long orderId, Order.OrderStatus status) {
        log.info("Updating order {} status to: {}", orderId, status);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        order.setStatus(status);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        log.info("Cancelling order: {}", orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (order.getStatus() == Order.OrderStatus.SHIPPED || order.getStatus() == Order.OrderStatus.DELIVERED) {
            throw new IllegalStateException("Cannot cancel order that is already shipped or delivered");
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    private BigDecimal calculateSubtotal(OrderDTO orderDTO) {
        if (orderDTO.getItems() == null || orderDTO.getItems().isEmpty()) {
            return BigDecimal.ZERO;
        }
        return orderDTO.getItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String generateOrderNumber() {
        return "ORD-" + LocalDateTime.now().getYear() + "-" +
               UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}
