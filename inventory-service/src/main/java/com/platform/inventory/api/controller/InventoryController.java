package com.platform.inventory.api.controller;

import com.platform.common.dto.ApiResponse;
import com.platform.inventory.application.service.InventoryService;
import com.platform.inventory.domain.entity.InventoryItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/stock")
    public ResponseEntity<ApiResponse<InventoryItem>> addStock(
            @RequestParam String productId,
            @RequestParam String productName,
            @RequestParam int quantity) {
        InventoryItem item = inventoryService.addStock(productId, productName, quantity);
        return ResponseEntity.ok(ApiResponse.success(item, "Stock updated"));
    }

    @GetMapping("/low-stock")
    public ResponseEntity<ApiResponse<List<InventoryItem>>> getLowStock(
            @RequestParam(defaultValue = "10") int threshold) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getLowStockItems(threshold)));
    }
}
