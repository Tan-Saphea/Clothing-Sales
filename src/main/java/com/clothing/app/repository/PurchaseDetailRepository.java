package com.clothing.app.repository;

import com.clothing.app.entity.PurchaseDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PurchaseDetailRepository extends JpaRepository<PurchaseDetail, Long> {
    @Query("SELECT pd FROM PurchaseDetail pd JOIN FETCH pd.variant v JOIN FETCH v.product p LEFT JOIN FETCH v.size s LEFT JOIN FETCH v.color c WHERE pd.purchase.purchaseId = :purchaseId")
    List<PurchaseDetail> findByPurchase_PurchaseId(@Param("purchaseId") Long purchaseId);

    List<PurchaseDetail> findByVariant_VariantId(Long variantId);

    boolean existsByPurchase_PurchaseIdAndVariant_VariantId(Long purchaseId, Long variantId);
}
