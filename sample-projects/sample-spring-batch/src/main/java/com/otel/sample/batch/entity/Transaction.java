package com.otel.sample.batch.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Transaction {

    private Long id;
    private String transactionId;
    private String accountNumber;
    private String description;
    private BigDecimal amount;
    private String type; // CREDIT, DEBIT
    private String status; // PENDING, PROCESSED, FAILED
    private LocalDateTime transactionDate;
    private LocalDateTime processedAt;

    // Default constructor
    public Transaction() {
    }

    // Full constructor
    public Transaction(Long id, String transactionId, String accountNumber, String description,
                       BigDecimal amount, String type, String status,
                       LocalDateTime transactionDate, LocalDateTime processedAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.accountNumber = accountNumber;
        this.description = description;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.transactionDate = transactionDate;
        this.processedAt = processedAt;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; }

    public LocalDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(LocalDateTime processedAt) { this.processedAt = processedAt; }

    // Business methods
    public boolean isValid() {
        return transactionId != null && !transactionId.isBlank() &&
               accountNumber != null && !accountNumber.isBlank() &&
               amount != null && amount.compareTo(BigDecimal.ZERO) != 0;
    }

    public BigDecimal getAbsoluteAmount() {
        return amount != null ? amount.abs() : BigDecimal.ZERO;
    }

    // Builder pattern
    public static TransactionBuilder builder() {
        return new TransactionBuilder();
    }

    public static class TransactionBuilder {
        private Long id;
        private String transactionId;
        private String accountNumber;
        private String description;
        private BigDecimal amount;
        private String type;
        private String status;
        private LocalDateTime transactionDate;
        private LocalDateTime processedAt;

        public TransactionBuilder id(Long id) { this.id = id; return this; }
        public TransactionBuilder transactionId(String transactionId) { this.transactionId = transactionId; return this; }
        public TransactionBuilder accountNumber(String accountNumber) { this.accountNumber = accountNumber; return this; }
        public TransactionBuilder description(String description) { this.description = description; return this; }
        public TransactionBuilder amount(BigDecimal amount) { this.amount = amount; return this; }
        public TransactionBuilder type(String type) { this.type = type; return this; }
        public TransactionBuilder status(String status) { this.status = status; return this; }
        public TransactionBuilder transactionDate(LocalDateTime transactionDate) { this.transactionDate = transactionDate; return this; }
        public TransactionBuilder processedAt(LocalDateTime processedAt) { this.processedAt = processedAt; return this; }

        public Transaction build() {
            return new Transaction(id, transactionId, accountNumber, description, amount, type, status, transactionDate, processedAt);
        }
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "id=" + id +
                ", transactionId='" + transactionId + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                ", amount=" + amount +
                ", type='" + type + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
