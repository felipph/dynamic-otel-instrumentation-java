package com.otel.sample.webmvc.controller;

import com.otel.sample.webmvc.dto.ProductDTO;
import com.otel.sample.webmvc.entity.Product;
import com.otel.sample.webmvc.service.ProductService;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductDTO productDTO) {
        log.info("REST request to create product: {}", productDTO.getSku());
        Product product = productService.createProduct(productDTO);
        return ResponseEntity.created(URI.create("/api/products/" + product.getId()))
                .body(product);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProduct(@PathVariable Long id) {
        log.info("REST request to get product: {}", id);
        return productService.getProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        log.info("REST request to get product by SKU: {}", sku);
        return productService.getProductBySku(sku)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAllProducts() {
        log.info("REST request to get all products");
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id,
                                                  @Valid @RequestBody ProductDTO productDTO) {
        log.info("REST request to update product: {}", id);
        Product product = productService.updateProduct(id, productDTO);
        return ResponseEntity.ok(product);
    }

    @PatchMapping("/{id}/stock")
    public ResponseEntity<Void> updateStock(@PathVariable Long id,
                                             @RequestParam int quantity) {
        log.info("REST request to update stock for product {}: delta={}", id, quantity);
        productService.updateStock(id, quantity);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        log.info("REST request to delete product: {}", id);
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }
}
