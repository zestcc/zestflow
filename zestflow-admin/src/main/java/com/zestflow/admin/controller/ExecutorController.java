package com.zestflow.admin.controller;

import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.model.vo.ExecutorRegistryVO;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.admin.service.ExecutorRegistryService;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/executors")
@RequiredArgsConstructor
public class ExecutorController {

    private final ExecutorRegistryService executorRegistryService;
    private final CollectorRegistryService collectorRegistryService;

    @GetMapping
    public Result<List<ExecutorRegistryVO>> listAll() {
        return Result.success(executorRegistryService.listAll());
    }

    @GetMapping("/{executorId}")
    public Result<ExecutorRegistryVO> getByExecutorId(@PathVariable String executorId) {
        return Result.success(executorRegistryService.getByExecutorId(executorId));
    }

    @PutMapping("/{executorId}/status")
    public Result<Void> updateExecutorStatus(@PathVariable String executorId,
                                             @RequestBody Map<String, Integer> body) {
        executorRegistryService.updateStatus(executorId, body.get("status"));
        return Result.success();
    }

    @GetMapping("/collectors")
    public Result<List<CollectorRegistryVO>> listCollectors() {
        return Result.success(collectorRegistryService.listAll());
    }

    @GetMapping("/collectors/{collectorId}")
    public Result<CollectorRegistryVO> getCollectorById(@PathVariable String collectorId) {
        return Result.success(collectorRegistryService.getByCollectorId(collectorId));
    }

    @PutMapping("/collectors/{collectorId}/status")
    public Result<Void> updateCollectorStatus(@PathVariable Long collectorId,
                                              @RequestBody Map<String, Integer> body) {
        collectorRegistryService.updateStatus(collectorId, body.get("status"));
        return Result.success();
    }

    @GetMapping("/apps")
    public Result<List<Map<String, String>>> listApps() {
        return Result.success(executorRegistryService.listDistinctApps());
    }
}
