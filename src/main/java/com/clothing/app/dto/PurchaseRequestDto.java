package com.clothing.app.dto;

import java.math.BigDecimal;
import java.util.List;

public class PurchaseRequestDto {
    private Long supplierId;
    private Long employeeId;
    private String note;
    private List<PurchaseItemRequestDto> items;
    private Boolean receiveImmediately = Boolean.TRUE;

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public List<PurchaseItemRequestDto> getItems() {
        return items;
    }

    public void setItems(List<PurchaseItemRequestDto> items) {
        this.items = items;
    }

    public Boolean getReceiveImmediately() {
        return receiveImmediately;
    }

    public void setReceiveImmediately(Boolean receiveImmediately) {
        this.receiveImmediately = receiveImmediately;
    }

    public static class PurchaseItemRequestDto {
        private Long variantId;
        private Integer quantity;
        private BigDecimal costPrice;

        public Long getVariantId() {
            return variantId;
        }

        public void setVariantId(Long variantId) {
            this.variantId = variantId;
        }

        public Integer getQuantity() {
            return quantity;
        }

        public void setQuantity(Integer quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getCostPrice() {
            return costPrice;
        }

        public void setCostPrice(BigDecimal costPrice) {
            this.costPrice = costPrice;
        }
    }
}
