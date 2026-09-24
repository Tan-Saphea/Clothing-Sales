package com.clothing.app.service;

import com.clothing.app.dto.PurchaseRequestDto;
import com.clothing.app.entity.Employee;
import com.clothing.app.entity.Product;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.entity.Purchase;
import com.clothing.app.entity.PurchaseDetail;
import com.clothing.app.entity.Supplier;
import com.clothing.app.repository.EmployeeRepository;
import com.clothing.app.repository.ProductVariantRepository;
import com.clothing.app.repository.PurchaseDetailRepository;
import com.clothing.app.repository.PurchaseRepository;
import com.clothing.app.repository.SupplierRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LowStockReorderTest {

    @Test
    void lowStockQuerySelectsCostPriceImageAndFiltersThreshold() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForList(any(String.class))).thenReturn(List.of());
        ReportService reportService = new ReportService(jdbcTemplate);

        reportService.getLowStock();

        verify(jdbcTemplate).queryForList(argThat(sql ->
                sql.contains("pv.COST_PRICE")
                && sql.contains("p.IMAGE_URL")
                && sql.contains("c.CATEGORY_NAME")
                && sql.contains("pv.STOCK_QTY <= 10")
                && sql.contains("ORDER BY pv.STOCK_QTY ASC")
        ));
    }

    @Test
    void createPurchaseWithImmediateReceiptCallsCompletePurchase() {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        PurchaseDetailRepository details = mock(PurchaseDetailRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        EmployeeRepository employees = mock(EmployeeRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        AuditTrailService audit = mock(AuditTrailService.class);

        Supplier supplier = new Supplier();
        supplier.setSupplierId(1L);
        supplier.setStatus("ACTIVE");
        when(suppliers.findById(1L)).thenReturn(Optional.of(supplier));

        Employee employee = new Employee();
        employee.setEmployeeId(2L);
        employee.setStatus("ACTIVE");
        when(employees.findById(2L)).thenReturn(Optional.of(employee));

        Purchase createdPurchase = new Purchase();
        createdPurchase.setPurchaseId(99L);
        createdPurchase.setStatus("PENDING");
        when(purchases.save(any(Purchase.class))).thenReturn(createdPurchase);
        when(purchases.findByIdForUpdate(99L)).thenReturn(Optional.of(createdPurchase));

        Product product = new Product();
        product.setIsActive(true);
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(101L);
        variant.setProduct(product);
        when(variants.findById(101L)).thenReturn(Optional.of(variant));
        when(details.findByPurchase_PurchaseId(99L)).thenReturn(List.of(new PurchaseDetail()));

        PurchaseService purchaseService = new PurchaseService(
                purchases, details, suppliers, employees, variants, procedures, audit);

        PurchaseRequestDto.PurchaseItemRequestDto item = new PurchaseRequestDto.PurchaseItemRequestDto();
        item.setVariantId(101L);
        item.setQuantity(20);
        item.setCostPrice(new BigDecimal("15.00"));

        PurchaseRequestDto request = new PurchaseRequestDto();
        request.setSupplierId(1L);
        request.setEmployeeId(2L);
        request.setReceiveImmediately(true);
        request.setItems(List.of(item));

        Purchase result = purchaseService.createPurchase(request);

        assertNotNull(result);
        verify(procedures).executeProcedure(eq("SP_ADD_PURCHASE_ITEM"), any());
        verify(procedures).executeProcedure(eq("SP_COMPLETE_PURCHASE"), any());
    }

    @Test
    void createPurchaseWithoutImmediateReceiptKeepsPending() {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        PurchaseDetailRepository details = mock(PurchaseDetailRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        EmployeeRepository employees = mock(EmployeeRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        AuditTrailService audit = mock(AuditTrailService.class);

        Supplier supplier = new Supplier();
        supplier.setSupplierId(1L);
        supplier.setStatus("ACTIVE");
        when(suppliers.findById(1L)).thenReturn(Optional.of(supplier));

        Employee employee = new Employee();
        employee.setEmployeeId(2L);
        employee.setStatus("ACTIVE");
        when(employees.findById(2L)).thenReturn(Optional.of(employee));

        Purchase createdPurchase = new Purchase();
        createdPurchase.setPurchaseId(100L);
        createdPurchase.setStatus("PENDING");
        when(purchases.save(any(Purchase.class))).thenReturn(createdPurchase);
        when(purchases.findByIdForUpdate(100L)).thenReturn(Optional.of(createdPurchase));

        Product product = new Product();
        product.setIsActive(true);
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(102L);
        variant.setProduct(product);
        when(variants.findById(102L)).thenReturn(Optional.of(variant));

        PurchaseService purchaseService = new PurchaseService(
                purchases, details, suppliers, employees, variants, procedures, audit);

        PurchaseRequestDto.PurchaseItemRequestDto item = new PurchaseRequestDto.PurchaseItemRequestDto();
        item.setVariantId(102L);
        item.setQuantity(15);
        item.setCostPrice(new BigDecimal("8.50"));

        PurchaseRequestDto request = new PurchaseRequestDto();
        request.setSupplierId(1L);
        request.setEmployeeId(2L);
        request.setReceiveImmediately(false);
        request.setItems(List.of(item));

        Purchase result = purchaseService.createPurchase(request);

        assertNotNull(result);
        assertEquals("PENDING", result.getStatus());
        verify(procedures).executeProcedure(eq("SP_ADD_PURCHASE_ITEM"), any());
        verify(procedures, never()).executeProcedure(eq("SP_COMPLETE_PURCHASE"), any());
    }
}
