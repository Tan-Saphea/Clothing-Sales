package com.clothing.app.service;

import com.clothing.app.entity.Payment;
import com.clothing.app.entity.Sale;
import com.clothing.app.repository.PaymentRepository;
import com.clothing.app.repository.SaleDetailRepository;
import com.clothing.app.repository.SaleRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SaleReversalService {

    private final SaleRepository saleRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final PaymentRepository paymentRepository;
    private final OracleProcedureService oracleProcedureService;
    private final AuditTrailService auditTrailService;

    public SaleReversalService(SaleRepository saleRepository,
                               SaleDetailRepository saleDetailRepository,
                               PaymentRepository paymentRepository,
                               OracleProcedureService oracleProcedureService,
                               AuditTrailService auditTrailService) {
        this.saleRepository = saleRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.paymentRepository = paymentRepository;
        this.oracleProcedureService = oracleProcedureService;
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public Sale cancelSale(Long saleId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Cancellation reason is required");
        }
        if (reason.trim().length() > 400) {
            throw new IllegalArgumentException("Cancellation reason must be 400 characters or fewer");
        }
        Sale sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        if ("CANCELLED".equals(sale.getStatus())) {
            throw new IllegalArgumentException("Sale is already cancelled");
        }
        if (!"PENDING".equals(sale.getStatus()) && !"COMPLETED".equals(sale.getStatus())) {
            throw new IllegalArgumentException("This sale cannot be cancelled");
        }

        if ("COMPLETED".equals(sale.getStatus())) {
            throw new IllegalArgumentException(
                    "Completed sales require an administrator-approved refund request; direct cancellation is not allowed");
        }

        sale.setStatus("CANCELLED");
        sale.setNote(appendCancellationReason(sale.getNote(), reason));
        Sale cancelled = saleRepository.save(sale);
        auditTrailService.record("SALE", "UPDATE", saleId,
                "Sale cancelled and applicable stock/payments reversed: " + reason.trim());
        return cancelled;
    }

    private String appendCancellationReason(String note, String reason) {
        String value = (note == null || note.isBlank() ? "" : note.trim() + " ")
                + "[CANCELLED: " + reason.trim() + "]";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
