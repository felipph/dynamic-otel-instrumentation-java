package com.otel.sample.batch.writer;

import com.otel.sample.batch.entity.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Writes processed transactions.
 * In a real application, this would write to a database or message queue.
 */
@Component
public class TransactionWriter implements ItemWriter<Transaction> {

    private static final Logger log = LoggerFactory.getLogger(TransactionWriter.class);

    private final List<Transaction> processedTransactions = new CopyOnWriteArrayList<>();

    @Override
    public void write(Chunk<? extends Transaction> chunk) throws Exception {
        log.info("Writing {} transactions", chunk.size());

        for (Transaction transaction : chunk) {
            log.info("Writing transaction: {} - Account: {} - Amount: {} - Status: {}",
                    transaction.getTransactionId(),
                    transaction.getAccountNumber(),
                    transaction.getAmount(),
                    transaction.getStatus());

            processedTransactions.add(transaction);
        }

        log.info("Batch write complete. Total processed: {}", processedTransactions.size());
    }

    public List<Transaction> getProcessedTransactions() {
        return new ArrayList<>(processedTransactions);
    }

    public void clear() {
        processedTransactions.clear();
    }

    public int getCount() {
        return processedTransactions.size();
    }
}
