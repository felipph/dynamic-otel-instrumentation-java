package com.otel.sample.batch.entity;

import java.math.BigDecimal;

public class TransactionSummary {

    private String accountNumber;
    private long transactionCount;
    private BigDecimal totalCredits;
    private BigDecimal totalDebits;
    private BigDecimal netAmount;

    // Default constructor
    public TransactionSummary() {
        this.totalCredits = BigDecimal.ZERO;
        this.totalDebits = BigDecimal.ZERO;
        this.netAmount = BigDecimal.ZERO;
    }

    // Full constructor
    public TransactionSummary(String accountNumber, long transactionCount,
                              BigDecimal totalCredits, BigDecimal totalDebits, BigDecimal netAmount) {
        this.accountNumber = accountNumber;
        this.transactionCount = transactionCount;
        this.totalCredits = totalCredits != null ? totalCredits : BigDecimal.ZERO;
        this.totalDebits = totalDebits != null ? totalDebits : BigDecimal.ZERO;
        this.netAmount = netAmount != null ? netAmount : BigDecimal.ZERO;
    }

    // Getters and Setters
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public long getTransactionCount() { return transactionCount; }
    public void setTransactionCount(long transactionCount) { this.transactionCount = transactionCount; }

    public BigDecimal getTotalCredits() { return totalCredits; }
    public void setTotalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; }

    public BigDecimal getTotalDebits() { return totalDebits; }
    public void setTotalDebits(BigDecimal totalDebits) { this.totalDebits = totalDebits; }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

    // Business methods
    public void addCredit(BigDecimal amount) {
        this.totalCredits = this.totalCredits.add(amount);
        this.transactionCount++;
        recalculateNet();
    }

    public void addDebit(BigDecimal amount) {
        this.totalDebits = this.totalDebits.add(amount);
        this.transactionCount++;
        recalculateNet();
    }

    private void recalculateNet() {
        this.netAmount = this.totalCredits.subtract(this.totalDebits);
    }

    // Builder pattern
    public static TransactionSummaryBuilder builder() {
        return new TransactionSummaryBuilder();
    }

    public static class TransactionSummaryBuilder {
        private String accountNumber;
        private long transactionCount;
        private BigDecimal totalCredits = BigDecimal.ZERO;
        private BigDecimal totalDebits = BigDecimal.ZERO;
        private BigDecimal netAmount = BigDecimal.ZERO;

        public TransactionSummaryBuilder accountNumber(String accountNumber) { this.accountNumber = accountNumber; return this; }
        public TransactionSummaryBuilder transactionCount(long transactionCount) { this.transactionCount = transactionCount; return this; }
        public TransactionSummaryBuilder totalCredits(BigDecimal totalCredits) { this.totalCredits = totalCredits; return this; }
        public TransactionSummaryBuilder totalDebits(BigDecimal totalDebits) { this.totalDebits = totalDebits; return this; }
        public TransactionSummaryBuilder netAmount(BigDecimal netAmount) { this.netAmount = netAmount; return this; }

        public TransactionSummary build() {
            return new TransactionSummary(accountNumber, transactionCount, totalCredits, totalDebits, netAmount);
        }
    }

    @Override
    public String toString() {
        return "TransactionSummary{" +
                "accountNumber='" + accountNumber + '\'' +
                ", transactionCount=" + transactionCount +
                ", totalCredits=" + totalCredits +
                ", totalDebits=" + totalDebits +
                ", netAmount=" + netAmount +
                '}';
    }
}
