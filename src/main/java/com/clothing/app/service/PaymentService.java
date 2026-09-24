package com.clothing.app.service;

import com.clothing.app.entity.Payment;
import com.clothing.app.entity.Sale;
import com.clothing.app.repository.PaymentRepository;
import com.clothing.app.repository.SaleRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999.99");

    private final PaymentRepository paymentRepository;
    private final SaleRepository saleRepository;
    private final OracleProcedureService oracleProcedureService;
    private final AuditTrailService auditTrailService;

    public PaymentService(PaymentRepository paymentRepository,
                         SaleRepository saleRepository,
                         OracleProcedureService oracleProcedureService,
                         AuditTrailService auditTrailService) {
        this.paymentRepository = paymentRepository;
        this.saleRepository = saleRepository;
        this.oracleProcedureService = oracleProcedureService;
        this.auditTrailService = auditTrailService;
    }

    public List<Payment> findAll() {
        return paymentRepository.findAll();
    }

    public Optional<Payment> findById(Long paymentId) {
        return paymentRepository.findById(paymentId);
    }

    public List<Payment> findBySaleId(Long saleId) {
        return paymentRepository.findBySale_SaleId(saleId);
    }

    public Map<Long, BigDecimal> findPaidAmountsForSales(Collection<Long> saleIds) {
        if (saleIds == null || saleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Object[]> rows = paymentRepository.findPaidTotalsBySaleIds(saleIds);
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : rows) {
            Long saleId = ((Number) row[0]).longValue();
            BigDecimal amount = (BigDecimal) row[1];
            result.put(saleId, amount != null ? amount : BigDecimal.ZERO);
        }
        return result;
    }

    public Map<String, BigDecimal> getBalance(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        if ("CANCELLED".equals(sale.getStatus())) {
            return Map.of("total", BigDecimal.ZERO, "paid", BigDecimal.ZERO, "remaining", BigDecimal.ZERO);
        }
        BigDecimal total = sale.getGrandTotal() == null ? BigDecimal.ZERO : sale.getGrandTotal();
        BigDecimal paid = paidAmount(saleId);
        return Map.of("total", total, "paid", paid, "remaining", total.subtract(paid).max(BigDecimal.ZERO));
    }

    private BigDecimal paidAmount(Long saleId) {
        return paymentRepository.findBySale_SaleId(saleId).stream()
                .filter(payment -> "PAID".equals(payment.getPaymentStatus()))
                .map(Payment::getAmount)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String normalizePaymentMethod(String method) {
        if (method == null) return "CASH";
        String m = method.trim().toUpperCase();
        if (m.contains("ABA")) return "ABA";
        if (m.contains("ACLEDA")) return "ACLEDA";
        if (m.contains("KHQR") || m.contains("QR")) return "KHQR";
        if (m.contains("CARD") || m.contains("CREDIT") || m.contains("DEBIT")) return "CARD";
        if (m.equals("CASH")) return "CASH";
        return "OTHER";
    }

    @Transactional
    public Payment recordPayment(Long saleId, BigDecimal amount, String paymentMethod, String referenceNo) {
        Sale sale = saleRepository.findByIdForPayment(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Sale not found: " + saleId));
        if (!"COMPLETED".equals(sale.getStatus())) {
            throw new IllegalArgumentException("Complete the sale before recording payment");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2
                || amount.compareTo(MAX_MONEY) > 0) {
            throw new IllegalArgumentException("Payment must be positive with at most two decimal places");
        }
        if (referenceNo != null && referenceNo.trim().length() > 100) {
            throw new IllegalArgumentException("Payment reference must be 100 characters or fewer");
        }
        BigDecimal paid = paidAmount(saleId);
        BigDecimal total = sale.getGrandTotal() == null ? BigDecimal.ZERO : sale.getGrandTotal();
        BigDecimal remaining = total.subtract(paid);
        if (remaining.signum() <= 0) {
            throw new IllegalArgumentException("This sale has no remaining balance");
        }
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException("Payment exceeds remaining balance of $" + remaining);
        }

        String validMethod = normalizePaymentMethod(paymentMethod);

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("P_SALE_ID", saleId)
                .addValue("P_AMOUNT", amount)
                .addValue("P_PAYMENT_METHOD", validMethod)
                .addValue("P_REFERENCE_NO", referenceNo == null || referenceNo.isBlank() ? null : referenceNo.trim());

        oracleProcedureService.executeProcedure("SP_RECORD_PAYMENT", params);

        Payment payment = paymentRepository.findTopBySale_SaleIdOrderByPaymentIdDesc(saleId)
                .orElseThrow(() -> new IllegalStateException("Payment was not recorded"));
        auditTrailService.record("PAYMENT", "INSERT", payment.getPaymentId(),
                "Payment of " + amount + " recorded for sale " + saleId + " using " + validMethod);
        return payment;
    }
}
