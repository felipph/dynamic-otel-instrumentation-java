package com.otel.sample.webmvc.service;

import com.otel.sample.webmvc.dto.ProductDTO;
import com.otel.sample.webmvc.entity.Product;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Product operations.
 * This interface will be instrumented - all implementations will be automatically traced.
 */
public interface ProductService {

    Product createProduct(ProductDTO productDTO);

    Optional<Product> getProductById(Long id);

    Optional<Product> getProductBySku(String sku);

    List<Product> getAllProducts();

    Product updateProduct(Long id, ProductDTO productDTO);

    void updateStock(Long productId, int quantity);

    void deleteProduct(Long id);
}
