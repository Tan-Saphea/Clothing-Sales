package com.clothing.app;

import com.clothing.app.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.demo-data.enabled=true",
        "app.demo-data.admin-password=TestOnly-Admin-Password-2026!",
        "app.demo-data.cashier-password=TestOnly-Cashier-Password-2026!"
})
class DataSeederVerificationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private SupplierRepository supplierRepository;

    @Autowired
    private SaleRepository saleRepository;

    @Autowired
    private SaleDetailRepository saleDetailRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private StockMovementRepository stockMovementRepository;

    @Test
    void verifyDatabaseHasRichData() {
        long categories = categoryRepository.count();
        long products = productRepository.count();
        long variants = productVariantRepository.count();
        long customers = customerRepository.count();
        long employees = employeeRepository.count();
        long suppliers = supplierRepository.count();
        long sales = saleRepository.count();
        long saleDetails = saleDetailRepository.count();
        long payments = paymentRepository.count();
        long stockMovements = stockMovementRepository.count();

        System.out.println("====== SEEDED DATA VERIFICATION ======");
        System.out.println("Categories: " + categories);
        System.out.println("Products: " + products);
        System.out.println("Variants: " + variants);
        System.out.println("Customers: " + customers);
        System.out.println("Employees: " + employees);
        System.out.println("Suppliers: " + suppliers);
        System.out.println("Sales: " + sales);
        System.out.println("Sale Details: " + saleDetails);
        System.out.println("Payments: " + payments);
        System.out.println("Stock Movements: " + stockMovements);
        System.out.println("======================================");

        assertTrue(categories >= 5, "Should have at least 5 categories");
        assertTrue(products >= 10, "Should have at least 10 products");
        assertTrue(variants >= 20, "Should have at least 20 variants");
        assertTrue(customers >= 5, "Should have at least 5 customers");
        assertTrue(employees >= 3, "Should have at least 3 employees");
        assertTrue(suppliers >= 4, "Should have at least 4 suppliers");
        assertTrue(sales >= 15, "Should have at least 15 sales");
        assertTrue(saleDetails >= 20, "Should have at least 20 sale detail lines");
        assertTrue(payments >= 15, "Should have at least 15 payments");
        assertTrue(stockMovements >= 20, "Should have at least 20 stock movements");
    }

}
