package com.clothing.app.service;

import com.clothing.app.entity.Payment;
import com.clothing.app.entity.RefundRequest;
import com.clothing.app.entity.Sale;
import com.clothing.app.repository.PaymentRepository;
import com.clothing.app.repository.RefundRequestRepository;
import com.clothing.app.repository.SaleDetailRepository;
import com.clothing.app.repository.SaleRepository;
import com.clothing.app.security.CurrentUserAccessService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RefundService {
    private static final List<String> OPEN_STATUSES = List.of("PENDING", "APPROVED");

    private final RefundRequestRepository refundRepository;
    private final SaleRepository saleRepository;
    private final SaleDetailRepository saleDetailRepository;
    private final PaymentRepository paymentRepository;
    private final OracleProcedureService oracleProcedureService;
    private final CurrentUserAccessService currentUserAccessService;
    private final AuditTrailService auditTrailService;

    public RefundService(RefundRequestRepository refundRepository, SaleRepository saleRepository,
                         SaleDetailRepository saleDetailRepository, PaymentRepository paymentRepository,
                         OracleProcedureService oracleProcedureService,
                         CurrentUserAccessService currentUserAccessService, AuditTrailService auditTrailService) {
        this.refundRepository = refundRepository;
        this.saleRepository = saleRepository;
        this.saleDetailRepository = saleDetailRepository;
        this.paymentRepository = paymentRepository;
        this.oracleProcedureService = oracleProcedureService;
        this.currentUserAccessService = currentUserAccessService;
        this.auditTrailService = auditTrailService;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'CASHIER')")
    public RefundRequest requestRefund(Long saleId, String reason, Authentication authentication) {
        validateText(reason, 400, "Refund reason");
        currentUserAccessService.assertCanAccessSale(authentication, saleId);
        Sale sale = saleRepository.findByIdForUpdate(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        if (!"COMPLETED".equals(sale.getStatus())) {
            throw new IllegalArgumentException("Only completed sales can be refunded");
        }
        if (refundRepository.existsBySale_SaleIdAndStatusIn(saleId, OPEN_STATUSES)) {
            throw new IllegalArgumentException("This sale already has an open refund request");
        }
        BigDecimal paid = paidAmount(saleId);
        if (paid.signum() <= 0) {
            throw new IllegalArgumentException("This sale has no paid amount to refund");
        }
        RefundRequest request = new RefundRequest();
        request.setSale(sale);
        request.setRequestedAmount(paid);
        request.setStatus("PENDING");
        request.setReason(reason.trim());
        request.setRequestedBy(authentication.getName());
        RefundRequest saved = refundRepository.save(request);
        auditTrailService.record("REFUND_REQUEST", "INSERT", saved.getRefundId(),
                "Refund requested for sale " + saleId + " amount " + paid);
        return saved;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<RefundRequest> listAll() {
        return refundRepository.findAllWithSale();
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public RefundRequest approve(Long refundId, String note, Authentication authentication) {
        RefundRequest request = locked(refundId);
        requireStatus(request, "PENDING");
        validateOptionalText(note, 400, "Decision note");
        request.setStatus("APPROVED");
        request.setDecidedBy(authentication.getName());
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionNote(blankToNull(note));
        auditTrailService.record("REFUND_REQUEST", "UPDATE", refundId, "Refund approved; awaiting completion confirmation");
        return refundRepository.save(request);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public RefundRequest reject(Long refundId, String note, Authentication authentication) {
        validateText(note, 400, "Rejection reason");
        RefundRequest request = locked(refundId);
        requireStatus(request, "PENDING");
        request.setStatus("REJECTED");
        request.setDecidedBy(authentication.getName());
        request.setDecidedAt(LocalDateTime.now());
        request.setDecisionNote(note.trim());
        auditTrailService.record("REFUND_REQUEST", "UPDATE", refundId, "Refund rejected: " + note.trim());
        return refundRepository.save(request);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public RefundRequest complete(Long refundId, String refundReference, Authentication authentication) {
        validateText(refundReference, 120, "Refund confirmation/reference");
        RefundRequest request = locked(refundId);
        requireStatus(request, "APPROVED");
        Sale sale = saleRepository.findByIdForUpdate(request.getSale().getSaleId())
                .orElseThrow(() -> new IllegalArgumentException("Sale not found"));
        if (!"COMPLETED".equals(sale.getStatus())) {
            throw new IllegalArgumentException("Sale is no longer eligible for refund completion");
        }
        BigDecimal paid = paidAmount(sale.getSaleId());
        if (paid.compareTo(request.getRequestedAmount()) != 0) {
            throw new IllegalArgumentException("Paid amount changed after refund approval; review the request again");
        }

        saleDetailRepository.findBySale_SaleId(sale.getSaleId()).forEach(detail ->
                oracleProcedureService.executePackageProcedure("PKG_INVENTORY", "ADD_STOCK",
                        new MapSqlParameterSource()
                                .addValue("P_VARIANT_ID", detail.getVariant().getVariantId())
                                .addValue("P_QUANTITY", detail.getQuantity())
                                .addValue("P_REFERENCE_TYPE", "RETURN")
                                .addValue("P_REFERENCE_ID", sale.getSaleId())
                                .addValue("P_NOTE", "Approved refund " + refundId)));

        List<Payment> payments = paymentRepository.findBySale_SaleId(sale.getSaleId());
        payments.stream().filter(p -> "PAID".equals(p.getPaymentStatus()))
                .forEach(p -> p.setPaymentStatus("REFUNDED"));
        paymentRepository.saveAll(payments);
        sale.setStatus("CANCELLED");
        sale.setNote(appendNote(sale.getNote(), "[REFUNDED: " + refundReference.trim() + "]"));
        saleRepository.save(sale);

        request.setStatus("COMPLETED");
        request.setRefundReference(refundReference.trim());
        request.setCompletedBy(authentication.getName());
        request.setCompletedAt(LocalDateTime.now());
        auditTrailService.record("REFUND_REQUEST", "UPDATE", refundId,
                "Refund completed for sale " + sale.getSaleId() + " reference " + refundReference.trim());
        return refundRepository.save(request);
    }

    private RefundRequest locked(Long id) {
        return refundRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Refund request not found: " + id));
    }
    private void requireStatus(RefundRequest request, String expected) {
        if (!expected.equals(request.getStatus())) throw new IllegalArgumentException("Refund request must be " + expected);
    }
    private BigDecimal paidAmount(Long saleId) {
        return paymentRepository.findBySale_SaleId(saleId).stream()
                .filter(p -> "PAID".equals(p.getPaymentStatus())).map(Payment::getAmount)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private void validateText(String value, int max, String field) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(field + " is required and must be " + max + " characters or fewer");
    }
    private void validateOptionalText(String value, int max, String field) {
        if (value != null && value.trim().length() > max) throw new IllegalArgumentException(field + " must be " + max + " characters or fewer");
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private String appendNote(String current, String addition) {
        String value = (current == null || current.isBlank() ? "" : current.trim() + " ") + addition;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
