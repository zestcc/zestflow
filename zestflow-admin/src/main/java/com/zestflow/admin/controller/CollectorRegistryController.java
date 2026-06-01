package com.zestflow.admin.controller;

import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registry/collector")
@RequiredArgsConstructor
public class CollectorRegistryController {

    private final CollectorRegistryService collectorRegistryService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        collectorRegistryService.register(dto);
        return Result.success();
    }

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@Valid @RequestBody HeartbeatDTO dto) {
        collectorRegistryService.heartbeat(dto);
        return Result.success();
    }

    @DeleteMapping("/{collectorId}")
    public Result<Void> deregister(@PathVariable String collectorId) {
        collectorRegistryService.deregister(collectorId);
        return Result.success();
    }
}
