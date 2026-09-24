package com.clothing.app.controller;

import com.clothing.app.dto.PurchaseRequestDto;
import com.clothing.app.entity.Purchase;
import com.clothing.app.entity.PurchaseDetail;
import com.clothing.app.service.PurchaseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api")
public class PurchaseApiController {

    private final PurchaseService purchaseService;

    public PurchaseApiController(PurchaseService purchaseService) {
        this.purchaseService = purchaseService;
    }

    private java.util.Map<String, Object> toPurchaseMap(Purchase p) {
        if (p == null) return java.util.Collections.emptyMap();
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("purchaseId", p.getPurchaseId());
        map.put("purchaseDate", p.getPurchaseDate() != null ? p.getPurchaseDate().toString() : "");
        map.put("totalAmount", p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO);
        map.put("status", p.getStatus() != null ? p.getStatus() : "");
        map.put("note", p.getNote() != null ? p.getNote() : "");
        map.put("createdAt", p.getCreatedAt() != null ? p.getCreatedAt().toString() : "");
        if (p.getSupplier() != null) {
            try {
                java.util.Map<String, Object> sMap = new java.util.LinkedHashMap<>();
                sMap.put("supplierId", p.getSupplier().getSupplierId());
                sMap.put("supplierName", p.getSupplier().getSupplierName());
                sMap.put("phone", p.getSupplier().getPhone());
                sMap.put("email", p.getSupplier().getEmail());
                map.put("supplier", sMap);
            } catch (Exception ignored) {}
        }
        if (p.getEmployee() != null) {
            try {
                java.util.Map<String, Object> eMap = new java.util.LinkedHashMap<>();
                eMap.put("employeeId", p.getEmployee().getEmployeeId());
                eMap.put("employeeName", p.getEmployee().getEmployeeName());
                map.put("employee", eMap);
            } catch (Exception ignored) {}
        }
        return map;
    }

    @GetMapping("/purchases")
    public List<java.util.Map<String, Object>> getAll() {
        return purchaseService.findAll().stream().map(this::toPurchaseMap).toList();
    }

    @GetMapping("/purchases/{purchaseId}")
    public ResponseEntity<java.util.Map<String, Object>> getById(@PathVariable Long purchaseId) {
        return purchaseService.findById(purchaseId)
                .map(this::toPurchaseMap)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/purchases/{purchaseId}/details")
    public List<java.util.Map<String, Object>> getDetails(@PathVariable Long purchaseId) {
        return purchaseService.findDetailsByPurchaseId(purchaseId).stream()
                .map(d -> {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("purchaseDetailId", d.getPurchaseDetailId());
                    map.put("quantity", d.getQuantity());
                    map.put("costPrice", d.getCostPrice());
                    map.put("subtotal", d.getSubtotal());
                    java.util.Map<String, Object> variantMap = new java.util.LinkedHashMap<>();
                    if (d.getVariant() != null) {
                        variantMap.put("variantId", d.getVariant().getVariantId());
                        variantMap.put("sku", d.getVariant().getSku());
                        java.util.Map<String, Object> prodMap = new java.util.LinkedHashMap<>();
                        if (d.getVariant().getProduct() != null) {
                            prodMap.put("productId", d.getVariant().getProduct().getProductId());
                            prodMap.put("productName", d.getVariant().getProduct().getProductName());
                        }
                        variantMap.put("product", prodMap);
                    }
                    map.put("variant", variantMap);
                    return map;
                })
                .toList();
    }

    @PostMapping("/purchases/pending")
    public ResponseEntity<java.util.Map<String, Object>> createPendingPurchase(@RequestParam Long supplierId,
                                                                               @RequestParam Long employeeId,
                                                                               @RequestParam(required = false) String note) {
        Purchase purchase = purchaseService.createPendingPurchase(supplierId, employeeId, note);
        return ResponseEntity.ok(toPurchaseMap(purchase));
    }

    @PostMapping("/purchases/{purchaseId}/items")
    public ResponseEntity<java.util.Map<String, Object>> addPurchaseItem(@PathVariable Long purchaseId,
                                                                        @RequestParam Long variantId,
                                                                        @RequestParam Integer quantity,
                                                                        @RequestParam BigDecimal costPrice) {
        Purchase purchase = purchaseService.addPurchaseItem(purchaseId, variantId, quantity, costPrice);
        return ResponseEntity.ok(toPurchaseMap(purchase));
    }

    @PostMapping("/purchases")
    public ResponseEntity<java.util.Map<String, Object>> createPurchase(@RequestBody PurchaseRequestDto request) {
        Purchase purchase = purchaseService.createPurchase(request);
        return ResponseEntity.ok(toPurchaseMap(purchase));
    }

    @PostMapping("/purchases/{purchaseId}/complete")
    public ResponseEntity<java.util.Map<String, Object>> completePurchase(@PathVariable Long purchaseId) {
        Purchase purchase = purchaseService.completePurchase(purchaseId);
        return ResponseEntity.ok(toPurchaseMap(purchase));
    }

    @PostMapping("/purchases/{purchaseId}/cancel")
    public ResponseEntity<java.util.Map<String, Object>> cancelPurchase(@PathVariable Long purchaseId,
                                                                        @RequestParam String reason) {
        Purchase purchase = purchaseService.cancelPendingPurchase(purchaseId, reason);
        return ResponseEntity.ok(toPurchaseMap(purchase));
    }
}
