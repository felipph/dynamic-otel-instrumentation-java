package com.otel.sample.webflux.controller;

import com.otel.sample.webflux.dto.ProductDTO;
import com.otel.sample.webflux.entity.Product;
import com.otel.sample.webflux.service.ProductService;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public Mono<Product> createProduct(@Valid @RequestBody ProductDTO productDTO) {
        log.info("REST request to create product: {}", productDTO.getSku());
        return productService.createProduct(productDTO);
    }

    @GetMapping("/{id}")
    public Mono<Product> getProduct(@PathVariable Long id) {
        log.info("REST request to get product: {}", id);
        return productService.getProductById(id);
    }

    @GetMapping("/sku/{sku}")
    public Mono<Product> getProductBySku(@PathVariable String sku) {
        log.info("REST request to get product by SKU: {}", sku);
        return productService.getProductBySku(sku);
    }

    @GetMapping
    public Flux<Product> getAllProducts() {
        log.info("REST request to get all products");
        return productService.getAllProducts();
    }

    @GetMapping("/available")
    public Flux<Product> getAvailableProducts() {
        log.info("REST request to get available products");
        return productService.getAvailableProducts();
    }

    @PutMapping("/{id}")
    public Mono<Product> updateProduct(@PathVariable Long id,
                                        @Valid @RequestBody ProductDTO productDTO) {
        log.info("REST request to update product: {}", id);
        return productService.updateProduct(id, productDTO);
    }

    @PatchMapping("/{id}/stock")
    public Mono<Void> updateStock(@PathVariable Long id,
                                   @RequestParam int quantity) {
        log.info("REST request to update stock for product {}: delta={}", id, quantity);
        return productService.updateStock(id, quantity);
    }

    @DeleteMapping("/{id}")
    public Mono<Void> deleteProduct(@PathVariable Long id) {
        log.info("REST request to delete product: {}", id);
        return productService.deleteProduct(id);
    }
}
