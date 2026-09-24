package com.clothing.app.dto;

public class ProductDto {
    private Long productId;
    private Long categoryId;
    private String categoryName;
    private String productName;
    private String description;
    private String imageUrl;
    private Boolean isActive;

    public ProductDto() {
    }

    public ProductDto(Long productId, Long categoryId, String categoryName, String productName, String description, Boolean isActive) {
        this(productId, categoryId, categoryName, productName, description, null, isActive);
    }

    public ProductDto(Long productId, Long categoryId, String categoryName, String productName, String description, String imageUrl, Boolean isActive) {
        this.productId = productId;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.productName = productName;
        this.description = description;
        this.imageUrl = imageUrl;
        this.isActive = isActive;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
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

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }
}
