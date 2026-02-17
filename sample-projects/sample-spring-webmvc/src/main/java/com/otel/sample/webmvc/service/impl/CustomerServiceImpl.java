package com.otel.sample.webmvc.service.impl;

import com.otel.sample.webmvc.dto.CustomerDTO;
import com.otel.sample.webmvc.entity.Customer;
import com.otel.sample.webmvc.repository.CustomerRepository;
import com.otel.sample.webmvc.service.CustomerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class CustomerServiceImpl implements CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerServiceImpl.class);

    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Override
    @Transactional
    public Customer createCustomer(CustomerDTO customerDTO) {
        log.info("Creating customer with email: {}", customerDTO.getEmail());

        if (customerRepository.existsByEmail(customerDTO.getEmail())) {
            throw new IllegalArgumentException("Customer with email already exists: " + customerDTO.getEmail());
        }

        Customer customer = new Customer();
        customer.setFirstName(customerDTO.getFirstName());
        customer.setLastName(customerDTO.getLastName());
        customer.setEmail(customerDTO.getEmail());
        customer.setPhone(customerDTO.getPhone());

        if (customerDTO.getAddress() != null) {
            Customer.Address address = new Customer.Address(
                    customerDTO.getAddress().getStreet(),
                    customerDTO.getAddress().getCity(),
                    customerDTO.getAddress().getState(),
                    customerDTO.getAddress().getZipCode(),
                    customerDTO.getAddress().getCountry()
            );
            customer.setAddress(address);
        }

        return customerRepository.save(customer);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerById(Long id) {
        log.info("Fetching customer by id: {}", id);
        return customerRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> getCustomerByEmail(String email) {
        log.info("Fetching customer by email: {}", email);
        return customerRepository.findByEmail(email);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> getAllCustomers() {
        log.info("Fetching all customers");
        return customerRepository.findAll();
    }

    @Override
    @Transactional
    public Customer updateCustomer(Long id, CustomerDTO customerDTO) {
        log.info("Updating customer with id: {}", id);

        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + id));

        customer.setFirstName(customerDTO.getFirstName());
        customer.setLastName(customerDTO.getLastName());
        customer.setEmail(customerDTO.getEmail());
        customer.setPhone(customerDTO.getPhone());

        if (customerDTO.getAddress() != null) {
            Customer.Address address = new Customer.Address(
                    customerDTO.getAddress().getStreet(),
                    customerDTO.getAddress().getCity(),
                    customerDTO.getAddress().getState(),
                    customerDTO.getAddress().getZipCode(),
                    customerDTO.getAddress().getCountry()
            );
            customer.setAddress(address);
        }

        return customerRepository.save(customer);
    }

    @Override
    @Transactional
    public void deleteCustomer(Long id) {
        log.info("Deleting customer with id: {}", id);
        customerRepository.deleteById(id);
    }
}
