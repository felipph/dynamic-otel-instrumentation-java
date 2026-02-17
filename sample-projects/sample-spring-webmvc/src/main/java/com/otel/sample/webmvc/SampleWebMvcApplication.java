package com.otel.sample.webmvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Sample Spring Boot Web MVC application demonstrating:
 * - REST Controllers with custom instrumentation
 * - Async method execution
 * - Virtual Threads (Java 21+)
 * - Service layer with business logic
 * - JPA Repository patterns
 */
@SpringBootApplication
@EnableAsync
public class SampleWebMvcApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleWebMvcApplication.class, args);
    }
}
