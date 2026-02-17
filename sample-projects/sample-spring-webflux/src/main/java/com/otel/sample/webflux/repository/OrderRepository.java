package com.otel.sample.webflux.repository;

import com.otel.sample.webflux.entity.Order;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface OrderRepository extends R2dbcRepository<Order, Long> {

    Mono<Order> findByOrderNumber(String orderNumber);

    Flux<Order> findByCustomerId(Long customerId);

    Flux<Order> findByStatus(String status);
}
