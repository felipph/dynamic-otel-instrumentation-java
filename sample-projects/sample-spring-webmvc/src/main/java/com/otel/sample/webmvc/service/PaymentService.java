package com.otel.sample.webmvc.service;

import java.math.BigDecimal;

/**
 * Service for processing payments asynchronously.
 * Demonstrates @Async method instrumentation.
 */
public interface PaymentService {

    /**
     * Process payment asynchronously.
     * Returns immediately, payment is processed in background.
     */
    void processPaymentAsync(Long orderId, BigDecimal amount, String paymentMethod);

    /**
     * Validate payment method synchronously.
     */
    boolean validatePaymentMethod(String paymentMethod);

    /**
     * Calculate shipping cost.
     */
    BigDecimal calculateShipping(String zipCode, BigDecimal orderTotal);
}
