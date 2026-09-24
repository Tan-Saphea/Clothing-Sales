package com.clothing.app.controller;

import com.clothing.app.dto.PaymentRequestDto;
import com.clothing.app.dto.SaleRequestDto;
import com.clothing.app.entity.Payment;
import com.clothing.app.entity.Sale;
import com.clothing.app.entity.SaleDetail;
import com.clothing.app.service.PaymentService;
import com.clothing.app.service.SaleService;
import com.clothing.app.service.SaleCheckoutService;
import com.clothing.app.security.CurrentUserAccessService;
import org.springframework.security.core.Authentication;
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
public class SaleApiController {

    private final SaleService saleService;
    private final PaymentService paymentService;
    private final CurrentUserAccessService currentUserAccessService;
    private final SaleCheckoutService saleCheckoutService;
    private final com.clothing.app.service.SaleReversalService saleReversalService;

    public SaleApiController(SaleService saleService, PaymentService paymentService,
                             CurrentUserAccessService currentUserAccessService,
                             SaleCheckoutService saleCheckoutService,
                             com.clothing.app.service.SaleReversalService saleReversalService) {
        this.saleService = saleService;
        this.paymentService = paymentService;
        this.currentUserAccessService = currentUserAccessService;
        this.saleCheckoutService = saleCheckoutService;
        this.saleReversalService = saleReversalService;
    }

    private java.util.Map<String, Object> toSaleMap(Sale sale) {
        if (sale == null) return java.util.Collections.emptyMap();
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("saleId", sale.getSaleId());
        map.put("saleDate", sale.getSaleDate() != null ? sale.getSaleDate().toString() : "");
        map.put("createdAt", sale.getCreatedAt() != null ? sale.getCreatedAt().toString() : "");
        map.put("status", sale.getStatus() != null ? sale.getStatus() : "");
        map.put("note", sale.getNote() != null ? sale.getNote() : "");
        map.put("subtotal", sale.getSubtotal() != null ? sale.getSubtotal() : BigDecimal.ZERO);
        map.put("discount", sale.getDiscount() != null ? sale.getDiscount() : BigDecimal.ZERO);
        map.put("grandTotal", sale.getGrandTotal() != null ? sale.getGrandTotal() : BigDecimal.ZERO);
        if (sale.getCustomer() != null) {
            try {
                java.util.Map<String, Object> custMap = new java.util.LinkedHashMap<>();
                custMap.put("customerId", sale.getCustomer().getCustomerId());
                custMap.put("customerName", sale.getCustomer().getCustomerName());
                custMap.put("phone", sale.getCustomer().getPhone());
                custMap.put("email", sale.getCustomer().getEmail());
                custMap.put("address", sale.getCustomer().getAddress());
                map.put("customer", custMap);
            } catch (Exception ignored) {}
        }
        if (sale.getEmployee() != null) {
            try {
                java.util.Map<String, Object> empMap = new java.util.LinkedHashMap<>();
                empMap.put("employeeId", sale.getEmployee().getEmployeeId());
                empMap.put("employeeName", sale.getEmployee().getEmployeeName());
                empMap.put("fullName", sale.getEmployee().getEmployeeName());
                empMap.put("phone", sale.getEmployee().getPhone());
                map.put("employee", empMap);
            } catch (Exception ignored) {}
        }
        return map;
    }

    @GetMapping("/sales")
    public List<java.util.Map<String, Object>> getAll(Authentication authentication) {
        List<Sale> sales = currentUserAccessService.isAdmin(authentication)
                ? saleService.findAll()
                : saleService.findAllForEmployee(currentUserAccessService.resolveCurrentEmployeeId(authentication));
        return sales.stream().map(this::toSaleMap).toList();
    }

    @GetMapping("/sales/{saleId}")
    public ResponseEntity<?> getById(@PathVariable Long saleId, Authentication authentication) {
        currentUserAccessService.assertCanAccessSale(authentication, saleId);
        return saleService.findById(saleId)
                .map(sale -> {
                    java.util.Map<String, Object> map = toSaleMap(sale);
                    var payments = paymentService.findBySaleId(saleId);
                    List<java.util.Map<String, Object>> payList = payments.stream().map(p -> {
                        java.util.Map<String, Object> pm = new java.util.LinkedHashMap<>();
                        pm.put("paymentId", p.getPaymentId());
                        pm.put("amount", p.getAmount());
                        pm.put("paymentMethod", p.getPaymentMethod());
                        pm.put("paymentStatus", p.getPaymentStatus());
                        pm.put("referenceNo", p.getReferenceNo());
                        pm.put("paymentDate", p.getPaymentDate() != null ? p.getPaymentDate().toString() : "");
                        return pm;
                    }).toList();
                    map.put("payments", payList);
                    var balance = paymentService.getBalance(saleId);
                    map.put("paidAmount", balance.get("paid"));
                    map.put("remainingBalance", balance.get("remaining"));
                    return ResponseEntity.ok(map);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/sales/{saleId}/details")
    public List<java.util.Map<String, Object>> getDetails(@PathVariable Long saleId, Authentication authentication) {
        currentUserAccessService.assertCanAccessSale(authentication, saleId);
        return saleService.findDetailsBySaleId(saleId).stream()
                .map(d -> {
                    java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
                    map.put("saleDetailId", d.getSaleDetailId());
                    map.put("quantity", d.getQuantity());
                    map.put("unitPrice", d.getUnitPrice());
                    map.put("discount", d.getDiscount());
                    map.put("subtotal", d.getSubtotal());
                    java.util.Map<String, Object> variantMap = new java.util.LinkedHashMap<>();
                    if (d.getVariant() != null) {
                        variantMap.put("variantId", d.getVariant().getVariantId());
                        variantMap.put("sku", d.getVariant().getSku());
                        if (d.getVariant().getSize() != null) {
                            variantMap.put("sizeName", d.getVariant().getSize().getSizeName());
                        }
                        if (d.getVariant().getColor() != null) {
                            variantMap.put("colorName", d.getVariant().getColor().getColorName());
                        }
                        java.util.Map<String, Object> prodMap = new java.util.LinkedHashMap<>();
                        if (d.getVariant().getProduct() != null) {
                            prodMap.put("productId", d.getVariant().getProduct().getProductId());
                            prodMap.put("productName", d.getVariant().getProduct().getProductName());
                            prodMap.put("imageUrl", d.getVariant().getProduct().getImageUrl());
                        }
                        variantMap.put("product", prodMap);
                    }
                    map.put("variant", variantMap);
                    return map;
                })
                .toList();
    }

    @PostMapping("/sales/pending")
    public ResponseEntity<java.util.Map<String, Object>> createPendingSale(@RequestParam(required = false) Long customerId,
                                                                          @RequestParam Long employeeId,
                                                                          @RequestParam(required = false) String note,
                                                                          Authentication authentication) {
        Long authorizedEmployeeId = currentUserAccessService.resolveSaleEmployeeId(authentication, employeeId);
        Sale sale = saleService.createPendingSale(customerId, authorizedEmployeeId, note);
        return ResponseEntity.ok(toSaleMap(sale));
    }

    @PostMapping("/sales/{saleId}/items")
    public ResponseEntity<java.util.Map<String, Object>> addSaleItem(@PathVariable Long saleId,
                                                                    @RequestParam Long variantId,
                                                                    @RequestParam Integer quantity,
                                                                    @RequestParam BigDecimal unitPrice,
                                                                    @RequestParam(required = false) BigDecimal discount,
                                                                    Authentication authentication) {
        currentUserAccessService.assertCanAccessSale(authentication, saleId);
        Sale sale = saleService.addSaleItem(saleId, variantId, quantity, unitPrice, discount);
        return ResponseEntity.ok(toSaleMap(sale));
    }

    @PostMapping("/sales")
    public ResponseEntity<java.util.Map<String, Object>> createSale(@RequestBody SaleRequestDto request,
                                                                    Authentication authentication) {
        request.setEmployeeId(currentUserAccessService.resolveSaleEmployeeId(authentication, request.getEmployeeId()));
        SaleCheckoutService.CheckoutResult checkout = saleCheckoutService.checkout(request);
        java.util.Map<String, Object> result = toSaleMap(checkout.sale());
        result.put("paidAmount", checkout.balance().get("paid"));
        result.put("remainingBalance", checkout.balance().get("remaining"));
        result.put("paymentRecorded", checkout.payment() != null);
        if (checkout.payment() != null) {
            result.put("paymentMethod", checkout.payment().getPaymentMethod());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/sales/{saleId}/complete")
    public ResponseEntity<java.util.Map<String, Object>> completeSale(@PathVariable Long saleId,
                                                                      Authentication authentication) {
        currentUserAccessService.assertCanAccessSale(authentication, saleId);
        Sale sale = saleService.completeSale(saleId);
        return ResponseEntity.ok(toSaleMap(sale));
    }

    @PostMapping("/sales/{saleId}/payments")
    public ResponseEntity<?> recordPayment(@PathVariable Long saleId, @RequestBody PaymentRequestDto request,
                                           Authentication authentication) {
        currentUserAccessService.assertCanAccessSale(authentication, saleId);
        Payment payment = paymentService.recordPayment(saleId, request.getAmount(), request.getPaymentMethod(), request.getReferenceNo());
        return ResponseEntity.ok(java.util.Map.of(
                "paymentId", payment.getPaymentId() != null ? payment.getPaymentId() : 0,
                "saleId", saleId,
                "amount", payment.getAmount(),
                "paymentMethod", payment.getPaymentMethod() != null ? payment.getPaymentMethod() : "CASH",
                "paymentStatus", payment.getPaymentStatus() != null ? payment.getPaymentStatus() : "PAID",
                "referenceNo", payment.getReferenceNo() != null ? payment.getReferenceNo() : ""
        ));
    }

    @GetMapping("/sales/{saleId}/balance")
    public java.util.Map<String, BigDecimal> getBalance(@PathVariable Long saleId, Authentication authentication) {
        currentUserAccessService.assertCanAccessSale(authentication, saleId);
        return paymentService.getBalance(saleId);
    }

    @PostMapping("/sales/{saleId}/cancel")
    public ResponseEntity<java.util.Map<String, Object>> cancelSale(@PathVariable Long saleId,
                                                                    @RequestParam String reason,
                                                                    Authentication authentication) {
        currentUserAccessService.assertCanCancelSale(authentication, saleId);
        return ResponseEntity.ok(toSaleMap(saleReversalService.cancelSale(saleId, reason)));
    }
}
