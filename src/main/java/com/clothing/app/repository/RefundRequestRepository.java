package com.clothing.app.repository;

import com.clothing.app.entity.RefundRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
    boolean existsBySale_SaleIdAndStatusIn(Long saleId, Collection<String> statuses);

    @Query("SELECT r FROM RefundRequest r JOIN FETCH r.sale s LEFT JOIN FETCH s.customer ORDER BY r.refundId DESC")
    List<RefundRequest> findAllWithSale();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RefundRequest r JOIN FETCH r.sale WHERE r.refundId = :refundId")
    Optional<RefundRequest> findByIdForUpdate(@Param("refundId") Long refundId);
}
