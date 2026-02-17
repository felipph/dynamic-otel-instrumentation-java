package com.otel.sample.microservices.messaging;

import com.otel.sample.microservices.model.Order;
import com.otel.sample.microservices.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order messages from RabbitMQ queue and processes them.
 */
@Component
public class OrderMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageConsumer.class);

    private final OrderService orderService;

    public OrderMessageConsumer(OrderService orderService) {
        this.orderService = orderService;
    }

    @RabbitListener(queues = "${app.queue.name:orders.queue}")
    public void receiveOrder(Order order) {
        log.info("Received order from queue: {} - Customer: {} - Amount: {}",
                order.getOrderId(), order.getCustomerId(), order.getAmount());

        try {
            Order processedOrder = orderService.processOrder(order);
            log.info("Order processed successfully: {} - Status: {}",
                    processedOrder.getOrderId(), processedOrder.getStatus());
        } catch (Exception e) {
            log.error("Failed to process order: {}", order.getOrderId(), e);
            // In a real application, you might want to send to a dead-letter queue
        }
    }
}
