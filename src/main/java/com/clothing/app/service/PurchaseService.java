package com.clothing.app.service;

import com.clothing.app.dto.PurchaseRequestDto;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.entity.Purchase;
import com.clothing.app.entity.PurchaseDetail;
import com.clothing.app.entity.Supplier;
import com.clothing.app.repository.EmployeeRepository;
import com.clothing.app.repository.ProductVariantRepository;
import com.clothing.app.repository.PurchaseDetailRepository;
import com.clothing.app.repository.PurchaseRepository;
import com.clothing.app.repository.SupplierRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class PurchaseService {

    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    private final PurchaseRepository purchaseRepository;
    private final PurchaseDetailRepository purchaseDetailRepository;
    private final SupplierRepository supplierRepository;
    private final EmployeeRepository employeeRepository;
    private final ProductVariantRepository productVariantRepository;
    private final OracleProcedureService oracleProcedureService;
    private final AuditTrailService auditTrailService;

    @PersistenceContext
    private EntityManager entityManager;

    public PurchaseService(PurchaseRepository purchaseRepository,
            PurchaseDetailRepository purchaseDetailRepository,
            SupplierRepository supplierRepository,
            EmployeeRepository employeeRepository,
            ProductVariantRepository productVariantRepository,
            OracleProcedureService oracleProcedureService,
            AuditTrailService auditTrailService) {
        this.purchaseRepository = purchaseRepository;
        this.purchaseDetailRepository = purchaseDetailRepository;
        this.supplierRepository = supplierRepository;
        this.employeeRepository = employeeRepository;
        this.productVariantRepository = productVariantRepository;
        this.oracleProcedureService = oracleProcedureService;
        this.auditTrailService = auditTrailService;
    }

    public List<Purchase> findAll() {
        return purchaseRepository.findAllWithDetails();
    }

    public Optional<Purchase> findById(Long purchaseId) {
        return purchaseRepository.findByIdWithDetails(purchaseId);
    }

    public List<PurchaseDetail> findDetailsByPurchaseId(Long purchaseId) {
        return purchaseDetailRepository.findByPurchase_PurchaseId(purchaseId);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Purchase createPendingPurchase(Long supplierId, Long employeeId, String note) {
        validateNote(note);
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + supplierId));
        if (!"ACTIVE".equals(supplier.getStatus())) {
            throw new IllegalArgumentException("Select an active supplier");
        }
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("Employee not found: " + employeeId));
        if (!"ACTIVE".equals(employee.getStatus())) {
            throw new IllegalArgumentException("Select an active employee");
        }

        Purchase purchase = new Purchase();
        purchase.setSupplier(supplier);
        purchase.setEmployee(employee);
        purchase.setPurchaseDate(LocalDate.now());
        purchase.setStatus("PENDING");
        purchase.setNote(note);
        purchase.setTotalAmount(BigDecimal.ZERO);
        Purchase saved = purchaseRepository.save(purchase);
        auditTrailService.record("PURCHASE", "INSERT", saved.getPurchaseId(), "Pending purchase created");
        return saved;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Purchase addPurchaseItem(Long purchaseId, Long variantId, Integer quantity, BigDecimal costPrice) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase not found: " + purchaseId));
        if (!"PENDING".equals(purchase.getStatus())) {
            throw new IllegalArgumentException("Only pending purchases can be changed");
        }
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new IllegalArgumentException("Variant not found: " + variantId));
        if (variant.getProduct() == null || !Boolean.TRUE.equals(variant.getProduct().getIsActive())) {
            throw new IllegalArgumentException("Cannot receive inventory for an inactive product");
        }
        if (purchaseDetailRepository.existsByPurchase_PurchaseIdAndVariant_VariantId(purchaseId, variantId)) {
            throw new IllegalArgumentException(
                    "The same product variant cannot appear more than once in a purchase order");
        }

        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        if (costPrice == null || costPrice.signum() < 0 || costPrice.scale() > 2
                || costPrice.compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException(
                    "Cost price must be a non-negative amount with at most two decimal places");
        }
        if (costPrice.multiply(BigDecimal.valueOf(quantity)).compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException("Purchase line total exceeds the supported monetary limit");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("P_PURCHASE_ID", purchaseId)
                .addValue("P_VARIANT_ID", variantId)
                .addValue("P_QUANTITY", quantity)
                .addValue("P_COST_PRICE", costPrice);

        oracleProcedureService.executeProcedure("SP_ADD_PURCHASE_ITEM", params);
        if (entityManager != null) {
            entityManager.flush();
            entityManager.refresh(purchase);
        }
        return purchase;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Purchase completePurchase(Long purchaseId) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase not found: " + purchaseId));
        if (!"PENDING".equals(purchase.getStatus())) {
            throw new IllegalArgumentException("Only pending purchases can be completed");
        }
        if (purchaseDetailRepository.findByPurchase_PurchaseId(purchaseId).isEmpty()) {
            throw new IllegalArgumentException("Add at least one item before completing the purchase");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("P_PURCHASE_ID", purchaseId);

        oracleProcedureService.executeProcedure("SP_COMPLETE_PURCHASE", params);
        if (entityManager != null) {
            entityManager.flush();
            entityManager.refresh(purchase);
        }
        auditTrailService.record("PURCHASE", "UPDATE", purchaseId,
                "Purchase completed with total amount " + purchase.getTotalAmount());
        return purchase;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Purchase createPurchase(PurchaseRequestDto request) {
        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("Purchase must contain at least one item");
        }
        long distinctVariants = request.getItems().stream()
                .filter(java.util.Objects::nonNull)
                .map(PurchaseRequestDto.PurchaseItemRequestDto::getVariantId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .count();
        if (distinctVariants != request.getItems().size()) {
            throw new IllegalArgumentException("Each product variant may appear only once in a purchase order");
        }
        Purchase purchase = createPendingPurchase(request.getSupplierId(), request.getEmployeeId(), request.getNote());

        for (PurchaseRequestDto.PurchaseItemRequestDto item : request.getItems()) {
            if (item == null)
                throw new IllegalArgumentException("Purchase item is missing");
            addPurchaseItem(purchase.getPurchaseId(), item.getVariantId(), item.getQuantity(), item.getCostPrice());
        }

        if (request.getReceiveImmediately() == null || Boolean.TRUE.equals(request.getReceiveImmediately())) {
            return completePurchase(purchase.getPurchaseId());
        }

        return purchase;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public Purchase cancelPendingPurchase(Long purchaseId, String reason) {
        Purchase purchase = purchaseRepository.findByIdForUpdate(purchaseId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase not found: " + purchaseId));
        if (!"PENDING".equals(purchase.getStatus())) {
            throw new IllegalArgumentException("Only pending purchases can be cancelled");
        }
        purchase.setStatus("CANCELLED");
        purchase.setNote(appendCancellationReason(purchase.getNote(), reason));
        Purchase cancelled = purchaseRepository.save(purchase);
        auditTrailService.record("PURCHASE", "UPDATE", purchaseId,
                "Pending purchase cancelled: " + reason.trim());
        return cancelled;
    }

    private String appendCancellationReason(String note, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }
        String value = (note == null || note.isBlank() ? "" : note.trim() + " ")
                + "[CANCELLED: " + reason.trim() + "]";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private void validateNote(String note) {
        if (note != null && note.length() > 500) {
            throw new IllegalArgumentException("Purchase note must be 500 characters or fewer");
        }
    }
}
