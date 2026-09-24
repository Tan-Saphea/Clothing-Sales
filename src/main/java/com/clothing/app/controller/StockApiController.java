package com.clothing.app.controller;

import com.clothing.app.dto.StockAdjustmentRequestDto;
import com.clothing.app.entity.ProductVariant;
import com.clothing.app.service.ReportService;
import com.clothing.app.service.StockAdjustmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stock")
public class StockApiController {

    private final ReportService reportService;
    private final StockAdjustmentService stockAdjustmentService;

    public StockApiController(ReportService reportService, StockAdjustmentService stockAdjustmentService) {
        this.reportService = reportService;
        this.stockAdjustmentService = stockAdjustmentService;
    }

    @GetMapping({"", "/overview", "/product-stock"})
    public List<Map<String, Object>> getProductStock() {
        return reportService.getProductStock();
    }

    @GetMapping("/low")
    public List<Map<String, Object>> getLowStock() {
        return reportService.getLowStock();
    }

    @GetMapping("/movements")
    public List<Map<String, Object>> getMovements() {
        return reportService.getStockMovements();
    }

    @PostMapping("/adjust")
    public ResponseEntity<Map<String, Object>> adjustStock(@Valid @RequestBody StockAdjustmentRequestDto request) {
        ProductVariant variant = stockAdjustmentService.adjustStock(request);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "success");
        response.put("message", "Stock adjusted successfully");
        response.put("variantId", variant.getVariantId());
        response.put("sku", variant.getSku());
        response.put("newStock", variant.getStockQty());
        return ResponseEntity.ok(response);
    }
}
