package com.otel.sample.batch.reader;

import com.otel.sample.batch.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Simulates reading transactions from an external source.
 * In a real application, this would read from a database, file, or message queue.
 */
@Component
public class TransactionReader implements ItemReader<Transaction> {

    private static final Logger log = LoggerFactory.getLogger(TransactionReader.class);

    private Queue<Transaction> transactions;
    private boolean initialized = false;

    @Override
    public Transaction read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        if (!initialized) {
            initialize();
        }

        Transaction transaction = transactions.poll();
        if (transaction != null) {
            log.info("Reading transaction: {} - Account: {} - Amount: {}",
                    transaction.getTransactionId(),
                    transaction.getAccountNumber(),
                    transaction.getAmount());
        }
        return transaction;
    }

    private void initialize() {
        log.info("Initializing transaction reader with sample data...");
        transactions = new ArrayDeque<>();

        // Generate sample transactions
        for (int i = 1; i <= 20; i++) {
            Transaction tx = Transaction.builder()
                    .id((long) i)
                    .transactionId("TXN-" + String.format("%05d", i))
                    .accountNumber("ACC-" + (i % 5 + 1))
                    .description("Transaction " + i)
                    .amount(BigDecimal.valueOf(Math.random() * 1000 - 500))
                    .type(i % 3 == 0 ? "DEBIT" : "CREDIT")
                    .status("PENDING")
                    .transactionDate(LocalDateTime.now().minusMinutes(i))
                    .build();
            transactions.add(tx);
        }

        initialized = true;
        log.info("Initialized with {} transactions", transactions.size());
    }

    public void reset() {
        initialized = false;
        transactions = null;
    }
}
