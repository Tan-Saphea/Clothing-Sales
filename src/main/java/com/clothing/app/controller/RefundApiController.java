package com.clothing.app.controller;

import com.clothing.app.entity.RefundRequest;
import com.clothing.app.service.RefundService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/refunds")
public class RefundApiController {
    private final RefundService refundService;
    public RefundApiController(RefundService refundService) { this.refundService = refundService; }

    @PostMapping("/request/{saleId}")
    public ResponseEntity<Map<String, Object>> request(@PathVariable Long saleId,
            @RequestBody Map<String, String> payload, Authentication authentication) {
        return ResponseEntity.ok(toMap(refundService.requestRefund(saleId, payload.get("reason"), authentication)));
    }

    @GetMapping
    public List<Map<String, Object>> list() { return refundService.listAll().stream().map(this::toMap).toList(); }

    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> payload,
            Authentication authentication) {
        return toMap(refundService.approve(id, payload == null ? null : payload.get("note"), authentication));
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody Map<String, String> payload,
            Authentication authentication) {
        return toMap(refundService.reject(id, payload.get("note"), authentication));
    }

    @PostMapping("/{id}/complete")
    public Map<String, Object> complete(@PathVariable Long id, @RequestBody Map<String, String> payload,
            Authentication authentication) {
        return toMap(refundService.complete(id, payload.get("refundReference"), authentication));
    }

    private Map<String, Object> toMap(RefundRequest r) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("refundId", r.getRefundId());
        result.put("saleId", r.getSale().getSaleId());
        result.put("customerName", r.getSale().getCustomer() == null ? "Walk-in" : r.getSale().getCustomer().getCustomerName());
        result.put("requestedAmount", r.getRequestedAmount());
        result.put("status", r.getStatus());
        result.put("reason", r.getReason());
        result.put("requestedBy", r.getRequestedBy());
        result.put("requestedAt", r.getRequestedAt());
        result.put("decidedBy", r.getDecidedBy());
        result.put("completedBy", r.getCompletedBy());
        result.put("refundReference", r.getRefundReference());
        result.put("decisionNote", r.getDecisionNote());
        return result;
    }
}
