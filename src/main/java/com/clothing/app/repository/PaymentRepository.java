package com.clothing.app.repository;

import com.clothing.app.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findBySale_SaleId(Long saleId);
    Optional<Payment> findTopBySale_SaleIdOrderByPaymentIdDesc(Long saleId);
    List<Payment> findByPaymentStatus(String paymentStatus);
    List<Payment> findByPaymentDateBetween(LocalDate start, LocalDate end);

    @Query("SELECT p.sale.saleId, COALESCE(SUM(p.amount), 0) FROM Payment p " +
           "WHERE p.paymentStatus = 'PAID' AND p.sale.saleId IN :saleIds " +
           "GROUP BY p.sale.saleId")
    List<Object[]> findPaidTotalsBySaleIds(@Param("saleIds") Collection<Long> saleIds);
}
