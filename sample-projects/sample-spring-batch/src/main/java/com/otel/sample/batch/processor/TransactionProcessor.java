package com.otel.sample.batch.processor;

import com.otel.sample.batch.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Processes transactions - validates and enriches them.
 */
@Component
public class TransactionProcessor implements ItemProcessor<Transaction, Transaction> {

    private static final Logger log = LoggerFactory.getLogger(TransactionProcessor.class);

    @Override
    public Transaction process(Transaction transaction) throws Exception {
        log.info("Processing transaction: {}", transaction.getTransactionId());

        // Validate transaction
        if (!transaction.isValid()) {
            log.warn("Invalid transaction detected: {}", transaction.getTransactionId());
            transaction.setStatus("FAILED");
            return null; // Skip invalid transactions
        }

        // Enrich transaction
        transaction.setProcessedAt(LocalDateTime.now());
        transaction.setStatus("PROCESSED");

        // Apply business rules
        if (transaction.getAbsoluteAmount().compareTo(java.math.BigDecimal.valueOf(10000)) > 0) {
            log.info("Large transaction detected: {} - Amount: {}",
                    transaction.getTransactionId(),
                    transaction.getAmount());
        }

        log.info("Transaction processed successfully: {} - Status: {}",
                transaction.getTransactionId(),
                transaction.getStatus());

        return transaction;
    }
}
