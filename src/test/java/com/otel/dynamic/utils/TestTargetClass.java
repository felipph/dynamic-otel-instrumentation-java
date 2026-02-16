package com.otel.dynamic.utils;

/**
 * Test target class for instrumentation testing.
 *
 * This class simulates a typical application class that would be instrumented
 * by the dynamic agent.
 */
public class TestTargetClass {

    /**
     * Simple test method with no arguments.
     */
    public String simpleMethod() {
        return "Hello from simpleMethod";
    }

    /**
     * Method with a complex object argument.
     */
    public void processBatch(BatchObject batch) {
        // Simulate processing
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Method with multiple arguments.
     */
    public String multiArgMethod(BatchObject batch, String content, int count) {
        return "Processed " + count + " items from batch " + batch.getBatchId();
    }

    /**
     * Method that throws an exception.
     */
    public void failingMethod() {
        throw new RuntimeException("Test exception");
    }

    /**
     * Test inner class for complex object simulation.
     */
    public static class BatchObject {
        private final String batchId;
        private final String rootId;
        private final int itemCount;

        public BatchObject(String batchId, String rootId, int itemCount) {
            this.batchId = batchId;
            this.rootId = rootId;
            this.itemCount = itemCount;
        }

        public String getBatchId() {
            return batchId;
        }

        public String getRootId() {
            return rootId;
        }

        public int getItemCount() {
            return itemCount;
        }

        @Override
        public String toString() {
            return "BatchObject{batchId='" + batchId + "', rootId='" + rootId + "', count=" + itemCount + "}";
        }
    }
}
