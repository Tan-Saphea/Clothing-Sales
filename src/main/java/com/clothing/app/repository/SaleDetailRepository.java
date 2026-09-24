package com.clothing.app.repository;

import com.clothing.app.entity.SaleDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SaleDetailRepository extends JpaRepository<SaleDetail, Long> {
    @Query("SELECT sd FROM SaleDetail sd JOIN FETCH sd.variant v JOIN FETCH v.product p LEFT JOIN FETCH v.size s LEFT JOIN FETCH v.color c WHERE sd.sale.saleId = :saleId")
    List<SaleDetail> findBySale_SaleId(@Param("saleId") Long saleId);

    List<SaleDetail> findByVariant_VariantId(Long variantId);

    boolean existsBySale_SaleIdAndVariant_VariantId(Long saleId, Long variantId);
}
