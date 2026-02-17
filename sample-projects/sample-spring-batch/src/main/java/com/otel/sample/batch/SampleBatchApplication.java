package com.otel.sample.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sample Spring Batch application demonstrating:
 * - Item Readers
 * - Item Processors
 * - Item Writers
 * - Job orchestration
 * - Step execution
 */
@SpringBootApplication
public class SampleBatchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SampleBatchApplication.class, args);
    }
}
