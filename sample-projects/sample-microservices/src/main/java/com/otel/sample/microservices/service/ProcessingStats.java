package com.otel.sample.microservices.service;

public class ProcessingStats {

    private final long totalOrders;
    private final long pendingOrders;
    private final long processedOrders;
    private final long failedOrders;

    public ProcessingStats(long totalOrders, long pendingOrders, long processedOrders, long failedOrders) {
        this.totalOrders = totalOrders;
        this.pendingOrders = pendingOrders;
        this.processedOrders = processedOrders;
        this.failedOrders = failedOrders;
    }

    public long getTotalOrders() { return totalOrders; }
    public long getPendingOrders() { return pendingOrders; }
    public long getProcessedOrders() { return processedOrders; }
    public long getFailedOrders() { return failedOrders; }
}
