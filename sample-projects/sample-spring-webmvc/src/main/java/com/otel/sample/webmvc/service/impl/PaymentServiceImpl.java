package com.otel.sample.webmvc.service.impl;

import com.otel.sample.webmvc.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    @Override
    @Async
    public void processPaymentAsync(Long orderId, BigDecimal amount, String paymentMethod) {
        log.info("Processing payment for order {} - Amount: {} - Method: {} - Thread: {}",
                orderId, amount, paymentMethod, Thread.currentThread().getName());

        try {
            // Simulate payment gateway processing
            TimeUnit.MILLISECONDS.sleep(500);

            // Simulate payment result
            boolean approved = Math.random() > 0.1; // 90% approval rate

            if (approved) {
                log.info("Payment APPROVED for order {}", orderId);
            } else {
                log.warn("Payment DECLINED for order {}", orderId);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Payment processing interrupted for order {}", orderId);
        }
    }

    @Override
    public boolean validatePaymentMethod(String paymentMethod) {
        log.info("Validating payment method: {}", paymentMethod);

        // Simulate validation
        return paymentMethod != null && (
                paymentMethod.equalsIgnoreCase("CREDIT_CARD") ||
                paymentMethod.equalsIgnoreCase("DEBIT_CARD") ||
                paymentMethod.equalsIgnoreCase("PAYPAL") ||
                paymentMethod.equalsIgnoreCase("PIX")
        );
    }

    @Override
    public BigDecimal calculateShipping(String zipCode, BigDecimal orderTotal) {
        log.info("Calculating shipping for zipCode: {} - Total: {}", zipCode, orderTotal);

        if (zipCode == null || zipCode.isBlank()) {
            return BigDecimal.valueOf(15.00); // Default shipping
        }

        // Simulate shipping calculation based on zip code
        if (zipCode.startsWith("01")) {
            return BigDecimal.ZERO; // Free shipping for capital
        } else if (zipCode.startsWith("02") || zipCode.startsWith("03")) {
            return BigDecimal.valueOf(5.00);
        } else if (orderTotal.compareTo(BigDecimal.valueOf(200)) > 0) {
            return BigDecimal.ZERO; // Free shipping for orders over 200
        } else {
            return BigDecimal.valueOf(15.00);
        }
    }
}
