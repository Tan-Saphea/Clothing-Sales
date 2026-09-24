package com.clothing.app.service;

import com.clothing.app.entity.Customer;
import com.clothing.app.repository.CustomerRepository;
import com.clothing.app.repository.SaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final SaleRepository saleRepository;
    private final AuditTrailService auditTrailService;

    public CustomerService(CustomerRepository customerRepository,
                           SaleRepository saleRepository,
                           AuditTrailService auditTrailService) {
        this.customerRepository = customerRepository;
        this.saleRepository = saleRepository;
        this.auditTrailService = auditTrailService;
    }

    public List<Customer> findAll() {
        return customerRepository.findAll();
    }

    public Optional<Customer> findById(Long customerId) {
        return customerRepository.findById(customerId);
    }

    public Optional<Customer> findByPhone(String phone) {
        return customerRepository.findByPhone(phone);
    }

    public List<Customer> search(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return findAll();
        }
        return customerRepository.searchCustomers(keyword.trim());
    }

    @Transactional
    public Customer save(Customer customer) {
        boolean creating = customer.getCustomerId() == null;
        if (customer.getCustomerName() == null || customer.getCustomerName().isBlank()) {
            throw new IllegalArgumentException("Customer name is required");
        }
        customer.setCustomerName(customer.getCustomerName().trim());
        validateLength(customer.getCustomerName(), 120, "Customer name");
        validateLength(customer.getGender(), 10, "Gender");
        validateLength(customer.getPhone(), 20, "Phone");
        validateLength(customer.getEmail(), 150, "Email");
        validateLength(customer.getAddress(), 255, "Address");
        Customer saved = customerRepository.save(customer);
        auditTrailService.record("CUSTOMER", creating ? "INSERT" : "UPDATE", saved.getCustomerId(),
                (creating ? "Customer created: " : "Customer updated: ") + saved.getCustomerName());
        return saved;
    }

    @Transactional
    public void deleteById(Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        if (!saleRepository.findByCustomer_CustomerId(customerId).isEmpty()) {
            throw new IllegalArgumentException("Customer has sales history and cannot be deleted");
        }
        customerRepository.delete(customer);
        auditTrailService.record("CUSTOMER", "DELETE", customerId, "Customer deleted: " + customer.getCustomerName());
    }

    private void validateLength(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(field + " must be " + max + " characters or fewer");
        }
    }
}
