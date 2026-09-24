package com.clothing.app.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class StockAdjustmentRequestDto {

    @NotNull(message = "Product variant is required")
    private Long variantId;

    private Integer newQuantity;

    private Integer quantityChange;

    @NotBlank(message = "Adjustment reason is required")
    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    private String reason;

    public StockAdjustmentRequestDto() {
    }

    public StockAdjustmentRequestDto(Long variantId, Integer newQuantity, Integer quantityChange, String reason) {
        this.variantId = variantId;
        this.newQuantity = newQuantity;
        this.quantityChange = quantityChange;
        this.reason = reason;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public Integer getNewQuantity() {
        return newQuantity;
    }

    public void setNewQuantity(Integer newQuantity) {
        this.newQuantity = newQuantity;
    }

    public Integer getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(Integer quantityChange) {
        this.quantityChange = quantityChange;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
