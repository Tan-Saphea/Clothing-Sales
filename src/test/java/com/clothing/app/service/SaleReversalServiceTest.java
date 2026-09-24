package com.clothing.app.service;

import com.clothing.app.entity.Payment;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.entity.Sale;
import com.clothing.app.entity.SaleDetail;
import com.clothing.app.repository.PaymentRepository;
import com.clothing.app.repository.SaleDetailRepository;
import com.clothing.app.repository.SaleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaleReversalServiceTest {

    @Test
    void completedSaleCancellationReturnsStockAndRefundsPaidPayments() {
        SaleRepository sales = mock(SaleRepository.class);
        SaleDetailRepository details = mock(SaleDetailRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        AuditTrailService audit = mock(AuditTrailService.class);
        Sale sale = new Sale();
        sale.setSaleId(9L);
        sale.setStatus("COMPLETED");
        ProductVariant variant = new ProductVariant();
        variant.setVariantId(4L);
        SaleDetail detail = new SaleDetail();
        detail.setVariant(variant);
        detail.setQuantity(3);
        Payment payment = new Payment();
        payment.setPaymentStatus("PAID");
        when(sales.findByIdForUpdate(9L)).thenReturn(Optional.of(sale));
        when(details.findBySale_SaleId(9L)).thenReturn(List.of(detail));
        when(payments.findBySale_SaleId(9L)).thenReturn(List.of(payment));
        when(sales.save(sale)).thenReturn(sale);
        SaleReversalService service = new SaleReversalService(sales, details, payments, procedures, audit);

        Sale result = service.cancelSale(9L, "Customer return");

        assertEquals("CANCELLED", result.getStatus());
        assertEquals("REFUNDED", payment.getPaymentStatus());
        verify(procedures).executePackageProcedure(eq("PKG_INVENTORY"), eq("ADD_STOCK"),
                any(MapSqlParameterSource.class));
        verify(payments).saveAll(List.of(payment));
        verify(audit).record(eq("SALE"), eq("UPDATE"), eq(9L), any(String.class));
    }

    @Test
    void pendingSaleCancellationDoesNotMoveStockOrRefundPayments() {
        SaleRepository sales = mock(SaleRepository.class);
        SaleDetailRepository details = mock(SaleDetailRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        AuditTrailService audit = mock(AuditTrailService.class);
        Sale sale = new Sale();
        sale.setSaleId(10L);
        sale.setStatus("PENDING");
        when(sales.findByIdForUpdate(10L)).thenReturn(Optional.of(sale));
        when(sales.save(sale)).thenReturn(sale);
        SaleReversalService service = new SaleReversalService(sales, details, payments, procedures, audit);

        service.cancelSale(10L, "Duplicate order");

        assertEquals("CANCELLED", sale.getStatus());
        verify(procedures, never()).executePackageProcedure(any(), any(), any());
        verify(payments, never()).saveAll(any());
    }
}
