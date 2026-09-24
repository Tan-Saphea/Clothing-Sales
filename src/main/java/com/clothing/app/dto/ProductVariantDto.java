package com.clothing.app.dto;

import java.math.BigDecimal;

public class ProductVariantDto {
    private Long variantId;
    private Long productId;
    private String productName;
    private Long categoryId;
    private String categoryName;
    private Long sizeId;
    private String sizeName;
    private Long colorId;
    private String colorName;
    private String sku;
    private BigDecimal costPrice;
    private BigDecimal salePrice;
    private Integer stockQty;
    private Boolean productActive;
    private String imageUrl;

    public ProductVariantDto() {
    }

    public ProductVariantDto(Long variantId, Long productId, String productName, Long sizeId, String sizeName,
                            Long colorId, String colorName, String sku, BigDecimal costPrice,
                            BigDecimal salePrice, Integer stockQty, Boolean productActive) {
        this(variantId, productId, productName, null, null, sizeId, sizeName, colorId, colorName, sku, costPrice, salePrice, stockQty, productActive, null);
    }

    public ProductVariantDto(Long variantId, Long productId, String productName, Long sizeId, String sizeName,
                            Long colorId, String colorName, String sku, BigDecimal costPrice,
                            BigDecimal salePrice, Integer stockQty, Boolean productActive, String imageUrl) {
        this(variantId, productId, productName, null, null, sizeId, sizeName, colorId, colorName, sku, costPrice, salePrice, stockQty, productActive, imageUrl);
    }

    public ProductVariantDto(Long variantId, Long productId, String productName, Long categoryId, String categoryName,
                            Long sizeId, String sizeName, Long colorId, String colorName, String sku,
                            BigDecimal costPrice, BigDecimal salePrice, Integer stockQty, Boolean productActive, String imageUrl) {
        this.variantId = variantId;
        this.productId = productId;
        this.productName = productName;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.sizeId = sizeId;
        this.sizeName = sizeName;
        this.colorId = colorId;
        this.colorName = colorName;
        this.sku = sku;
        this.costPrice = costPrice;
        this.salePrice = salePrice;
        this.stockQty = stockQty;
        this.productActive = productActive;
        this.imageUrl = imageUrl;
    }

    public Long getVariantId() {
        return variantId;
    }

    public void setVariantId(Long variantId) {
        this.variantId = variantId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Long getSizeId() {
        return sizeId;
    }

    public void setSizeId(Long sizeId) {
        this.sizeId = sizeId;
    }

    public String getSizeName() {
        return sizeName;
    }

    public void setSizeName(String sizeName) {
        this.sizeName = sizeName;
    }

    public Long getColorId() {
        return colorId;
    }

    public void setColorId(Long colorId) {
        this.colorId = colorId;
    }

    public String getColorName() {
        return colorName;
    }

    public void setColorName(String colorName) {
        this.colorName = colorName;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public BigDecimal getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(BigDecimal costPrice) {
        this.costPrice = costPrice;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public Integer getStockQty() {
        return stockQty;
    }

    public void setStockQty(Integer stockQty) {
        this.stockQty = stockQty;
    }

    public Boolean getProductActive() {
        return productActive;
    }

    public void setProductActive(Boolean productActive) {
        this.productActive = productActive;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
}
