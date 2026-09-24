package com.clothing.app.service;

import com.clothing.app.dto.SaleRequestDto;
import com.clothing.app.entity.Customer;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.entity.Sale;
import com.clothing.app.entity.SaleDetail;
import com.clothing.app.repository.CustomerRepository;
import com.clothing.app.repository.EmployeeRepository;
import com.clothing.app.repository.ProductVariantRepository;
import com.clothing.app.repository.SaleDetailRepository;
import com.clothing.app.repository.SaleRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SaleService {

    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    private final SaleRepository saleRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final CustomerRepository customerRepository;
    private final EmployeeRepository employeeRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OracleProcedureService oracleProcedureService;
    private final AuditTrailService auditTrailService;

    @PersistenceContext
    private EntityManager entityManager;

    public SaleService(SaleRepository saleRepository,
                      SaleDetailRepository saleDetailRepository,
                      CustomerRepository customerRepository,
                      EmployeeRepository employeeRepository,
                      ProductVariantRepository productVariantRepository,
                      OracleProcedureService oracleProcedureService,
                      AuditTrailService auditTrailService) {
        this.saleRepository = saleRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.customerRepository = customerRepository;
        this.employeeRepository = employeeRepository;
        this.productVariantRepository = productVariantRepository;
        this.oracleProcedureService = oracleProcedureService;
        this.auditTrailService = auditTrailService;
    }

    public List<Sale> findAll() {
        return saleRepository.findAllWithDetails();
    }

    public List<Sale> findAllForEmployee(Long employeeId) {
        return saleRepository.findAllWithDetailsByEmployeeId(employeeId);
    }

    public Optional<Sale> findById(Long saleId) {
        return saleRepository.findByIdWithDetails(saleId);
    }

    public List<SaleDetail> findDetailsBySaleId(Long saleId) {
        return saleDetailRepository.findBySale_SaleId(saleId);
    }

    @Transactional
    public Sale createPendingSale(Long customerId, Long employeeId, String note) {
        validateNote(note);
        Customer customer = null;
        if (customerId != null) {
            customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        }

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new IllegalArgumentException("Select an active employee");
        }

        Sale sale = new Sale();
        sale.setCustomer(customer);
        sale.setEmployee(employee);
        sale.setSaleDate(LocalDate.now());
        sale.setStatus("PENDING");
        sale.setNote(note);
        sale.setSubtotal(BigDecimal.ZERO);
        sale.setDiscount(BigDecimal.ZERO);
        sale.setGrandTotal(BigDecimal.ZERO);
        Sale saved = saleRepository.save(sale);
        auditTrailService.record("SALE", "INSERT", saved.getSaleId(), "Pending sale created");
        return saved;
    }

    @Transactional
    public Sale addSaleItem(Long saleId, Long variantId, Integer quantity, BigDecimal unitPrice, BigDecimal discount) {
        Sale sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        if (!"PENDING".equals(sale.getStatus())) {
            throw new IllegalArgumentException("Only pending sales can be changed");
        }
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));
        if (variant.getProduct() == null || !Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
            throw new IllegalArgumentException("This product is inactive");
        }
        if (saleDetailRepository.existsBySale_SaleIdAndVariant_VariantId(saleId, variantId)) {
            throw new IllegalArgumentException("The same product variant cannot appear more than once in a sale");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        BigDecimal catalogPrice = variant.getSalePrice();
        if (catalogPrice == null || catalogPrice.signum() < 0 || catalogPrice.scale() > 2) {
            throw new IllegalArgumentException("The product does not have a valid catalog price");
        }
        BigDecimal lineDiscount = discount == null ? BigDecimal.ZERO : discount;
        if (unitPrice != null && unitPrice.compareTo(catalogPrice) != 0) {
            throw new IllegalArgumentException("The submitted price does not match the current catalog price");
        }
        if (lineDiscount.signum() != 0) {
            throw new IllegalArgumentException("Line discounts require a manager-approved discount workflow");
        }
        if (catalogPrice.multiply(BigDecimal.valueOf(quantity)).compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException("Sale line total exceeds the supported monetary limit");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("P_SALE_ID", saleId)
                .addValue("P_VARIANT_ID", variantId)
                .addValue("P_QUANTITY", quantity)
                .addValue("P_UNIT_PRICE", catalogPrice)
                .addValue("P_LINE_DISCOUNT", BigDecimal.ZERO);

        oracleProcedureService.executeProcedure("SP_ADD_SALE_ITEM", params);
        entityManager.flush();
        entityManager.refresh(sale);
        return sale;
    }

    @Transactional
    public Sale completeSale(Long saleId) {
        Sale sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        if (!"PENDING".equals(sale.getStatus())) {
            throw new IllegalArgumentException("Only pending sales can be completed");
        }
        if (saleDetailRepository.findBySale_SaleId(saleId).isEmpty()) {
            throw new IllegalArgumentException("Add at least one item before completing the sale");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("P_SALE_ID", saleId);

        oracleProcedureService.executeProcedure("SP_COMPLETE_SALE", params);
        entityManager.flush();
        entityManager.refresh(sale);
        auditTrailService.record("SALE", "UPDATE", saleId,
                "Sale completed with grand total " + sale.getGrandTotal());
        return sale;
    }

    @Transactional
    public Sale createSale(SaleRequestDto request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Sale must contain at least one item");
        }
        long distinctVariants = request.getItems().stream()
                .filter(java.util.Objects::nonNull)
                .map(SaleRequestDto.SaleItemRequestDto::getVariantId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        if (distinctVariants != request.getItems().size()) {
            throw new IllegalArgumentException("Each product variant may appear only once in a sale");
        }
        Sale sale = createPendingSale(request.getCustomerId(), request.getEmployeeId(), request.getNote());

        for (SaleRequestDto.SaleItemRequestDto item : request.getItems()) {
            if (item == null) throw new IllegalArgumentException("Sale item is missing");
            addSaleItem(sale.getSaleId(), item.getVariantId(), item.getQuantity(), item.getUnitPrice(), item.getDiscount());
        }

        return completeSale(sale.getSaleId());
    }

    private void validateNote(String note) {
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("Sale note must be 500 characters or fewer");
        }
    }
}
