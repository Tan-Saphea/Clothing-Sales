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
    void completedSaleCancellationRequiresAdminApprovedRefundRequest() {
        SaleRepository sales = mock(SaleRepository.class);
        SaleDetailRepository details = mock(SaleDetailRepository.class);
        PaymentRepository payments = mock(PaymentRepository.class);
        OracleProcedureService procedures = mock(OracleProcedureService.class);
        AuditTrailService audit = mock(AuditTrailService.class);
        Sale sale = new Sale();
        sale.setSaleId(9L);
        sale.setStatus("COMPLETED");
        when(sales.findByIdForUpdate(9L)).thenReturn(Optional.of(sale));
        SaleReversalService service = new SaleReversalService(sales, details, payments, procedures, audit);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.cancelSale(9L, "Customer return"));
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
