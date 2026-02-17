package com.otel.sample.webmvc.controller;

import com.otel.sample.webmvc.service.PaymentService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/process")
    public ResponseEntity<Void> processPayment(@RequestParam Long orderId,
                                                @RequestParam BigDecimal amount,
                                                @RequestParam String paymentMethod) {
        log.info("REST request to process payment for order: {}", orderId);
        paymentService.processPaymentAsync(orderId, amount, paymentMethod);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/validate")
    public ResponseEntity<Boolean> validatePaymentMethod(@RequestParam String method) {
        log.info("REST request to validate payment method: {}", method);
        boolean valid = paymentService.validatePaymentMethod(method);
        return ResponseEntity.ok(valid);
    }

    @GetMapping("/shipping")
    public ResponseEntity<BigDecimal> calculateShipping(@RequestParam(required = false) String zipCode,
                                                         @RequestParam BigDecimal total) {
        log.info("REST request to calculate shipping for zipCode: {} - total: {}", zipCode, total);
        BigDecimal shipping = paymentService.calculateShipping(zipCode, total);
        return ResponseEntity.ok(shipping);
    }
}
