package com.otel.sample.microservices.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dummy in-memory product repository.
 * In a real application, this would use a database.
 */
@Repository
public class ProductRepository {

    private static final Logger log = LoggerFactory.getLogger(ProductRepository.class);

    private final Map<String, ProductInfo> products = new ConcurrentHashMap<>();

    public ProductRepository() {
        // Initialize with some dummy products
        products.put("PROD-001", new ProductInfo("PROD-001", "Laptop", new BigDecimal("999.99")));
        products.put("PROD-002", new ProductInfo("PROD-002", "Mouse", new BigDecimal("29.99")));
        products.put("PROD-003", new ProductInfo("PROD-003", "Keyboard", new BigDecimal("79.99")));
        products.put("PROD-004", new ProductInfo("PROD-004", "Monitor", new BigDecimal("299.99")));
        products.put("PROD-005", new ProductInfo("PROD-005", "Headset", new BigDecimal("149.99")));
    }

    public Optional<ProductInfo> findById(String productId) {
        log.debug("Finding product by id: {}", productId);
        return Optional.ofNullable(products.get(productId));
    }

    public boolean existsById(String productId) {
        return products.containsKey(productId);
    }

    public static class ProductInfo {
        private final String productId;
        private final String name;
        private final BigDecimal price;

        public ProductInfo(String productId, String name, BigDecimal price) {
            this.productId = productId;
            this.name = name;
            this.price = price;
        }

        public String getProductId() { return productId; }
        public String getName() { return name; }
        public BigDecimal getPrice() { return price; }
    }
}
