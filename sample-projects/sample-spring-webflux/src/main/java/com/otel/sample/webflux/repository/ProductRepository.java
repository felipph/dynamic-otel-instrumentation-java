package com.otel.sample.webflux.repository;

import com.otel.sample.webflux.entity.Product;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ProductRepository extends R2dbcRepository<Product, Long> {

    Mono<Product> findBySku(String sku);

    Mono<Boolean> existsBySku(String sku);

    Flux<Product> findByStockQuantityGreaterThan(Integer minStock);
}
