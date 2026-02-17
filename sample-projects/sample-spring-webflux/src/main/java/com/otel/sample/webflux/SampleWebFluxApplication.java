package com.otel.sample.webflux;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sample Spring Boot WebFlux application demonstrating:
 * - Reactive REST Controllers
 * - Reactor patterns (Mono, Flux)
 * - R2DBC reactive database access
 * - Non-blocking service layer
 */
@SpringBootApplication
public class SampleWebFluxApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleWebFluxApplication.class, args);
    }
}
