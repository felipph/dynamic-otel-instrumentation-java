package com.otel.sample.webmvc.controller;

import com.otel.sample.webmvc.dto.CustomerDTO;
import com.otel.sample.webmvc.entity.Customer;
import com.otel.sample.webmvc.service.CustomerService;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<Customer> createCustomer(@Valid @RequestBody CustomerDTO customerDTO) {
        log.info("REST request to create customer: {}", customerDTO.getEmail());
        Customer customer = customerService.createCustomer(customerDTO);
        return ResponseEntity.created(URI.create("/api/customers/" + customer.getId()))
                .body(customer);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomer(@PathVariable Long id) {
        log.info("REST request to get customer: {}", id);
        return customerService.getCustomerById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Customer>> getAllCustomers() {
        log.info("REST request to get all customers");
        List<Customer> customers = customerService.getAllCustomers();
        return ResponseEntity.ok(customers);
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<Customer> getCustomerByEmail(@PathVariable String email) {
        log.info("REST request to get customer by email: {}", email);
        return customerService.getCustomerByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable Long id,
                                                    @Valid @RequestBody CustomerDTO customerDTO) {
        log.info("REST request to update customer: {}", id);
        Customer customer = customerService.updateCustomer(id, customerDTO);
        return ResponseEntity.ok(customer);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        log.info("REST request to delete customer: {}", id);
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}
