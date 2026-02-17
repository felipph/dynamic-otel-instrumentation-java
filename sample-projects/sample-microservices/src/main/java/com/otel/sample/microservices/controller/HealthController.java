package com.otel.sample.microservices.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @Value("${spring.application.name:sample-microservices}")
    private String applicationName;

    @Value("${server.port:8080}")
    private String serverPort;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("service", applicationName);
        health.put("port", serverPort);
        health.put("timestamp", LocalDateTime.now());
        return health;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", applicationName);
        info.put("description", "Sample microservices with HTTP and RabbitMQ messaging");
        info.put("version", "1.0.0");
        info.put("port", serverPort);
        return info;
    }
}
