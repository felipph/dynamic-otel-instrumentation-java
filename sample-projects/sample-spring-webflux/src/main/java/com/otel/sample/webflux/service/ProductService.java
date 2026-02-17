package com.otel.sample.webflux.service;

import com.otel.sample.webflux.dto.ProductDTO;
import com.otel.sample.webflux.entity.Product;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Reactive service interface for Product operations.
 */
public interface ProductService {

    Mono<Product> createProduct(ProductDTO productDTO);

    Mono<Product> getProductById(Long id);

    Mono<Product> getProductBySku(String sku);

    Flux<Product> getAllProducts();

    Flux<Product> getAvailableProducts();

    Mono<Product> updateProduct(Long id, ProductDTO productDTO);

    Mono<Void> updateStock(Long productId, int quantity);

    Mono<Void> deleteProduct(Long id);
}
