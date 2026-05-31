package com.zestflow.test.controller;

import com.zestflow.common.model.Result;
import com.zestflow.test.dto.InventoryRequest;
import com.zestflow.test.dto.InventoryResponse;
import com.zestflow.test.service.BizOrchestrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 库存中心 Controller — 库存查询/批量导入/迭代处理
 */
@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final BizOrchestrationService orch;

    // ==================== S03: 库存查询 ====================

    @GetMapping("/check")
    public Result<InventoryResponse> checkStock(@RequestParam(defaultValue = "SKU001") String sku,
                                                 @RequestParam(defaultValue = "1") int qty) {
        String chainCode = "inv-check-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = Map.of("sku", sku, "quantity", qty);

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("check", "checkStock")
        ), List.of(), params);

        return Result.success(InventoryResponse.builder()
                .sku(sku)
                .availableQty(100)
                .sufficient(result.getStatus() == 4)
                .status(statusText(result.getStatus()))
                .costMs(result.getCostMs())
                .build());
    }

    // ==================== S06: 批量处理（小） ====================

    @PostMapping("/batch-update")
    public Result<InventoryResponse> batchUpdate(@Valid @RequestBody InventoryRequest req) {
        String chainCode = "inv-batch-" + UUID.randomUUID().toString().substring(0, 8);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            items.add(Map.of("sku", req.getSku() + "-" + i, "qty", 10));
        }
        Map<String, Object> params = Map.of("items", items, "sku", req.getSku());

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("start", "biz001"),
                BizOrchestrationService.iteratorNode("iter", Map.of(
                        "dataSource", "items", "itemName", "item",
                        "subNodes", List.of(Map.of("id", "subItem", "label", "处理商品",
                                "type", "NORMAL", "component", "biz003"))
                )),
                BizOrchestrationService.normalNode("end", "biz004")
        ), List.of(
                BizOrchestrationService.edge("start", "iter"),
                BizOrchestrationService.edge("iter", "end")
        ), params);

        return Result.success(InventoryResponse.builder()
                .sku(req.getSku())
                .status(statusText(result.getStatus()))
                .importedCount(3)
                .costMs(result.getCostMs()).build());
    }

    // ==================== L06: 大批量迭代(100) ====================

    @PostMapping("/batch-import")
    public Result<InventoryResponse> batchImport(@Valid @RequestBody InventoryRequest req) {
        String chainCode = "inv-import-" + UUID.randomUUID().toString().substring(0, 8);
        int count = req.getQuantity() > 0 ? req.getQuantity() : 100;
        List<Map<String, Object>> items = IntStream.range(0, count)
                .mapToObj(i -> Map.<String, Object>of("sku", req.getSku() + "-" + i, "qty", 1))
                .collect(Collectors.toList());
        Map<String, Object> params = Map.of("items", items, "sku", req.getSku());

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("start", "biz001"),
                BizOrchestrationService.iteratorNode("iter", Map.of(
                        "dataSource", "items", "itemName", "item",
                        "subNodes", List.of(Map.of("id", "subItem", "label", "处理商品",
                                "type", "NORMAL", "component", "biz003"))
                )),
                BizOrchestrationService.normalNode("end", "biz004")
        ), List.of(
                BizOrchestrationService.edge("start", "iter"),
                BizOrchestrationService.edge("iter", "end")
        ), params);

        return Result.success(InventoryResponse.builder()
                .sku(req.getSku())
                .status(statusText(result.getStatus()))
                .importedCount(count)
                .costMs(result.getCostMs()).build());
    }

    // ==================== E03: 空迭代器 ====================

    @PostMapping("/batch-empty")
    public Result<InventoryResponse> batchEmpty(@RequestBody InventoryRequest req) {
        String chainCode = "inv-empty-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> params = Map.of("items", List.of(), "sku", req.getSku() != null ? req.getSku() : "EMPTY");

        var result = orch.loadAndExecute(chainCode, List.of(
                BizOrchestrationService.normalNode("start", "biz001"),
                BizOrchestrationService.iteratorNode("iter", Map.of(
                        "dataSource", "items", "itemName", "item",
                        "subNodes", List.of(Map.of("id", "subItem", "label", "子项",
                                "type", "NORMAL", "component", "biz003"))
                )),
                BizOrchestrationService.normalNode("end", "biz004")
        ), List.of(
                BizOrchestrationService.edge("start", "iter"),
                BizOrchestrationService.edge("iter", "end")
        ), params);

        return Result.success(InventoryResponse.builder()
                .sku("EMPTY")
                .status(statusText(result.getStatus()))
                .importedCount(0)
                .costMs(result.getCostMs()).build());
    }

    private String statusText(int status) {
        switch (status) {
            case 4: return "SUCCESS";
            case 5: return "FAILED";
            default: return "UNKNOWN";
        }
    }
}
