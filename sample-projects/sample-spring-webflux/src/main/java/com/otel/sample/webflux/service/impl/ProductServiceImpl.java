package com.otel.sample.webflux.service.impl;

import com.otel.sample.webflux.dto.ProductDTO;
import com.otel.sample.webflux.entity.Product;
import com.otel.sample.webflux.repository.ProductRepository;
import com.otel.sample.webflux.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public Mono<Product> createProduct(ProductDTO productDTO) {
        log.info("Creating product with SKU: {}", productDTO.getSku());

        return productRepository.existsBySku(productDTO.getSku())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Product with SKU already exists: " + productDTO.getSku()));
                    }
                    Product product = new Product();
                    product.setSku(productDTO.getSku());
                    product.setName(productDTO.getName());
                    product.setDescription(productDTO.getDescription());
                    product.setPrice(productDTO.getPrice());
                    product.setStockQuantity(productDTO.getStockQuantity() != null ? productDTO.getStockQuantity() : 0);
                    product.setCreatedAt(LocalDateTime.now());
                    return productRepository.save(product);
                });
    }

    @Override
    public Mono<Product> getProductById(Long id) {
        log.info("Fetching product by id: {}", id);
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Product not found: " + id)));
    }

    @Override
    public Mono<Product> getProductBySku(String sku) {
        log.info("Fetching product by SKU: {}", sku);
        return productRepository.findBySku(sku)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Product not found: " + sku)));
    }

    @Override
    public Flux<Product> getAllProducts() {
        log.info("Fetching all products");
        return productRepository.findAll();
    }

    @Override
    public Flux<Product> getAvailableProducts() {
        log.info("Fetching available products (stock > 0)");
        return productRepository.findByStockQuantityGreaterThan(0);
    }

    @Override
    public Mono<Product> updateProduct(Long id, ProductDTO productDTO) {
        log.info("Updating product with id: {}", id);

        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Product not found: " + id)))
                .flatMap(product -> {
                    product.setName(productDTO.getName());
                    product.setDescription(productDTO.getDescription());
                    product.setPrice(productDTO.getPrice());
                    if (productDTO.getStockQuantity() != null) {
                        product.setStockQuantity(productDTO.getStockQuantity());
                    }
                    return productRepository.save(product);
                });
    }

    @Override
    public Mono<Void> updateStock(Long productId, int quantity) {
        log.info("Updating stock for product {}: delta={}", productId, quantity);

        return productRepository.findById(productId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Product not found: " + productId)))
                .flatMap(product -> {
                    int newQuantity = product.getStockQuantity() + quantity;
                    if (newQuantity < 0) {
                        return Mono.error(new IllegalArgumentException("Insufficient stock"));
                    }
                    product.setStockQuantity(newQuantity);
                    return productRepository.save(product);
                })
                .then();
    }

    @Override
    public Mono<Void> deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);
        return productRepository.deleteById(id);
    }
}
