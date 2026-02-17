package com.otel.sample.microservices.messaging;

import com.otel.sample.microservices.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Produces order messages to RabbitMQ queue.
 */
@Component
public class OrderMessageProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderMessageProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final Queue orderQueue;

    public OrderMessageProducer(RabbitTemplate rabbitTemplate, Queue orderQueue) {
        this.rabbitTemplate = rabbitTemplate;
        this.orderQueue = orderQueue;
    }

    public void sendOrder(Order order) {
        log.info("Sending order to queue: {} - Queue: {}", order.getOrderId(), orderQueue.getName());
        rabbitTemplate.convertAndSend(orderQueue.getName(), order);
        log.debug("Order sent successfully: {}", order.getOrderId());
    }
}
