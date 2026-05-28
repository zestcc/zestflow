package com.zestflow.admin.controller;

import com.zestflow.admin.service.RegistryService;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/registry")
@RequiredArgsConstructor
public class RegistryController {

    private final RegistryService registryService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        registryService.register(dto);
        return Result.success();
    }

    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@Valid @RequestBody HeartbeatDTO dto) {
        registryService.heartbeat(dto);
        return Result.success();
    }

    @DeleteMapping("/{executorId}")
    public Result<Void> deregister(@PathVariable String executorId) {
        registryService.deregister(executorId);
        return Result.success();
    }

    @PutMapping("/{executorId}/status")
    public Result<Void> updateStatus(@PathVariable String executorId,
                                     @RequestParam Integer status) {
        registryService.updateStatus(executorId, status);
        return Result.success();
    }
}
