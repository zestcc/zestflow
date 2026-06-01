package com.zestflow.admin.controller;

import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin UI 采集器管理端点
 */
@RestController
@RequestMapping("/api/collectors")
@RequiredArgsConstructor
public class CollectorAdminController {

    private final CollectorRegistryService collectorRegistryService;

    @GetMapping
    public Result<List<CollectorRegistryVO>> listAll() {
        return Result.success(collectorRegistryService.listAll());
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        collectorRegistryService.updateStatus(id, body.get("status"));
        return Result.success();
    }
}
