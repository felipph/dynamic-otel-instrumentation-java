package com.otel.sample.webmvc.service;

import com.otel.sample.webmvc.dto.CustomerDTO;
import com.otel.sample.webmvc.entity.Customer;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for Customer operations.
 * This interface will be instrumented - all implementations will be automatically traced.
 */
public interface CustomerService {

    Customer createCustomer(CustomerDTO customerDTO);

    Optional<Customer> getCustomerById(Long id);

    Optional<Customer> getCustomerByEmail(String email);

    List<Customer> getAllCustomers();

    Customer updateCustomer(Long id, CustomerDTO customerDTO);

    void deleteCustomer(Long id);
}
