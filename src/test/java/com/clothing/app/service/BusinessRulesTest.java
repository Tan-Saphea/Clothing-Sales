package com.clothing.app.service;

import com.clothing.app.entity.Category;
import com.clothing.app.entity.Payment;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.entity.Product;
import com.clothing.app.entity.Purchase;
import com.clothing.app.entity.Sale;
import com.clothing.app.dto.PurchaseRequestDto;
import com.clothing.app.dto.SaleRequestDto;
import com.clothing.app.repository.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class BusinessRulesTest {

    @Test
    void saleRejectsDiscountAboveLineTotalBeforeProcedureRuns() {
        SaleRepository sales = mock(SaleRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        Sale sale = new Sale();
        sale.setStatus("PENDING");
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        Product product = new Product();
        product.setIsActive(true);
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSalePrice(new BigDecimal("5.00"));
        when(variants.findById(2L)).thenReturn(Optional.of(variant));
        SaleService service = new SaleService(sales, mock(SaleDetailRepository.class),
                mock(CustomerRepository.class), mock(EmployeeRepository.class), variants, procedures,
                mock(AuditTrailService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.addSaleItem(1L, 2L, 1, new BigDecimal("5.00"), new BigDecimal("5.01")));
        verifyNoInteractions(procedures);
    }

    @Test
    void saleRejectsClientPriceOverrideBeforeProcedureRuns() {
        SaleRepository sales = mock(SaleRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        Sale sale = new Sale();
        sale.setStatus("PENDING");
        Product product = new Product();
        product.setIsActive(true);
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSalePrice(new BigDecimal("25.00"));
        when(sales.findByIdForUpdate(1L)).thenReturn(Optional.of(sale));
        when(variants.findById(2L)).thenReturn(Optional.of(variant));
        SaleService service = new SaleService(sales, mock(SaleDetailRepository.class),
                mock(CustomerRepository.class), mock(EmployeeRepository.class), variants, procedures,
                mock(AuditTrailService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.addSaleItem(1L, 2L, 1, BigDecimal.ZERO, BigDecimal.ZERO));
        verifyNoInteractions(procedures);
    }

    @Test
    void completedPurchaseCannotReceiveAnotherItem() {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        Purchase purchase = new Purchase();
        purchase.setStatus("COMPLETED");
        when(purchases.findByIdForUpdate(1L)).thenReturn(Optional.of(purchase));
        PurchaseService service = new PurchaseService(purchases, mock(PurchaseDetailRepository.class),
                mock(SupplierRepository.class), mock(EmployeeRepository.class),
                mock(ProductVariantRepository.class), procedures, mock(AuditTrailService.class));

        assertThrows(IllegalArgumentException.class,
                () -> service.addPurchaseItem(1L, 2L, 1, new BigDecimal("3.00")));
        verifyNoInteractions(procedures);
    }

    @Test
    void paymentCannotExceedRemainingBalance() {
        SaleRepository sales = mock(SaleRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        Sale sale = new Sale();
        sale.setStatus("COMPLETED");
        sale.setGrandTotal(new BigDecimal("10.00"));
        Payment paid = new Payment();
        paid.setPaymentStatus("PAID");
        paid.setAmount(new BigDecimal("7.00"));
        when(sales.findByIdForPayment(1L)).thenReturn(Optional.of(sale));
        when(payments.findBySale_SaleId(1L)).thenReturn(List.of(paid));
        PaymentService service = new PaymentService(payments, sales, procedures, mock(AuditTrailService.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.recordPayment(1L, new BigDecimal("3.01"), "CASH", null));
        assertEquals("Payment exceeds remaining balance of $3.00", error.getMessage());
        verifyNoInteractions(procedures);
    }

    @Test
    void checkoutCompletesSaleAndRecordsRequestedPayment() {
        SaleService sales = mock(SaleService.class);
        PaymentService payments = mock(PaymentService.class);
        SaleRequestDto request = new SaleRequestDto();
        request.setPaymentAmount(new BigDecimal("25.00"));
        request.setPaymentMethod("KHQR");
        request.setReferenceNo("QR-1001");
        Sale sale = new Sale();
        sale.setSaleId(10L);
        Payment payment = new Payment();
        payment.setAmount(new BigDecimal("25.00"));
        when(sales.createSale(request)).thenReturn(sale);
        when(payments.recordPayment(10L, new BigDecimal("25.00"), "KHQR", "QR-1001"))
                .thenReturn(payment);
        when(payments.getBalance(10L)).thenReturn(Map.of(
                "total", new BigDecimal("25.00"),
                "paid", new BigDecimal("25.00"),
                "remaining", BigDecimal.ZERO));

        SaleCheckoutService.CheckoutResult result = new SaleCheckoutService(sales, payments).checkout(request);

        assertEquals(sale, result.sale());
        assertEquals(payment, result.payment());
        assertEquals(BigDecimal.ZERO, result.balance().get("remaining"));
    }

    @Test
    void checkoutCanCompleteSaleWithoutPayment() {
        SaleService sales = mock(SaleService.class);
        PaymentService payments = mock(PaymentService.class);
        SaleRequestDto request = new SaleRequestDto();
        Sale sale = new Sale();
        sale.setSaleId(11L);
        when(sales.createSale(request)).thenReturn(sale);
        when(payments.getBalance(11L)).thenReturn(Map.of(
                "total", new BigDecimal("15.00"),
                "paid", BigDecimal.ZERO,
                "remaining", new BigDecimal("15.00")));

        SaleCheckoutService.CheckoutResult result = new SaleCheckoutService(sales, payments).checkout(request);

        assertEquals(null, result.payment());
        verify(payments, never()).recordPayment(anyLong(), any(), any(), any());
    }

    @Test
    void categoryCannotBeDeletedWhenProductsAssigned() {
        CategoryRepository categories = mock(CategoryRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        OracleSessionContextService oracleContext = mock(OracleSessionContextService.class);
        Category category = new Category();
        category.setCategoryId(5L);
        category.setCategoryName("Summer Apparel");
        when(categories.findById(5L)).thenReturn(Optional.of(category));
        when(products.findByCategory_CategoryId(5L)).thenReturn(List.of(new Product()));

        CategoryService service = new CategoryService(categories, products, oracleContext);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.deleteById(5L));
        assertEquals("Cannot delete category 'Summer Apparel' because products are assigned to it. Reassign or delete the products first.", ex.getMessage());
        verify(categories, never()).delete(any());
    }

    @Test
    void purchaseRejectsDuplicateVariantsInSameOrder() {
        PurchaseRepository purchases = mock(PurchaseRepository.class);
        PurchaseDetailRepository details = mock(PurchaseDetailRepository.class);
        SupplierRepository suppliers = mock(SupplierRepository.class);
        EmployeeRepository employees = mock(EmployeeRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        AuditTrailService audit = mock(AuditTrailService.class);

        PurchaseService service = new PurchaseService(purchases, details, suppliers, employees, variants, procedures, audit);

        PurchaseRequestDto.PurchaseItemRequestDto item1 = new PurchaseRequestDto.PurchaseItemRequestDto();
        item1.setVariantId(10L);
        item1.setQuantity(5);
        item1.setCostPrice(new BigDecimal("12.00"));

        PurchaseRequestDto.PurchaseItemRequestDto item2 = new PurchaseRequestDto.PurchaseItemRequestDto();
        item2.setVariantId(10L);
        item2.setQuantity(10);
        item2.setCostPrice(new BigDecimal("12.00"));

        PurchaseRequestDto request = new PurchaseRequestDto();
        request.setSupplierId(1L);
        request.setEmployeeId(2L);
        request.setItems(List.of(item1, item2));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.createPurchase(request));
        assertEquals("Each product variant may appear only once in a purchase order", ex.getMessage());
        verifyNoInteractions(procedures);
    }
}
