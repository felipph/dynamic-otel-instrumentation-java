package com.otel.sample.microservices;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sample Microservices application demonstrating:
 * - HTTP REST endpoints
 * - RabbitMQ messaging (producer and consumer)
 * - Inter-service communication patterns
 *
 * This application can run as:
 * - Order Service (receives orders via HTTP, sends to queue)
 * - Processor Service (consumes from queue, processes orders)
 */
@SpringBootApplication
public class SampleMicroservicesApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleMicroservicesApplication.class, args);
    }
}
