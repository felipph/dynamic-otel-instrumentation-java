package com.otel.sample.webmvc.service.impl;

import com.otel.sample.webmvc.dto.ProductDTO;
import com.otel.sample.webmvc.entity.Product;
import com.otel.sample.webmvc.repository.ProductRepository;
import com.otel.sample.webmvc.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    @Transactional
    public Product createProduct(ProductDTO productDTO) {
        log.info("Creating product with SKU: {}", productDTO.getSku());

        if (productRepository.existsBySku(productDTO.getSku())) {
            throw new IllegalArgumentException("Product with SKU already exists: " + productDTO.getSku());
        }

        Product product = new Product();
        product.setSku(productDTO.getSku());
        product.setName(productDTO.getName());
        product.setDescription(productDTO.getDescription());
        product.setPrice(productDTO.getPrice());
        product.setStockQuantity(productDTO.getStockQuantity() != null ? productDTO.getStockQuantity() : 0);

        return productRepository.save(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        log.info("Fetching product by id: {}", id);
        return productRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Product> getProductBySku(String sku) {
        log.info("Fetching product by SKU: {}", sku);
        return productRepository.findBySku(sku);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        log.info("Fetching all products");
        return productRepository.findAll();
    }

    @Override
    @Transactional
    public Product updateProduct(Long id, ProductDTO productDTO) {
        log.info("Updating product with id: {}", id);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        product.setName(productDTO.getName());
        product.setDescription(productDTO.getDescription());
        product.setPrice(productDTO.getPrice());
        if (productDTO.getStockQuantity() != null) {
            product.setStockQuantity(productDTO.getStockQuantity());
        }

        return productRepository.save(product);
    }

    @Override
    @Transactional
    public void updateStock(Long productId, int quantity) {
        log.info("Updating stock for product {}: delta={}", productId, quantity);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productId));

        int newQuantity = product.getStockQuantity() + quantity;
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Insufficient stock");
        }

        product.setStockQuantity(newQuantity);
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void deleteProduct(Long id) {
        log.info("Deleting product with id: {}", id);
        productRepository.deleteById(id);
    }
}
