package com.zestflow.admin.controller;

import com.zestflow.admin.model.dto.ModuleCreateDTO;
import com.zestflow.admin.model.dto.ModuleUpdateDTO;
import com.zestflow.admin.model.vo.ExecutorRegistryVO;
import com.zestflow.admin.model.vo.ModuleVO;
import com.zestflow.admin.service.ExecutorRegistryService;
import com.zestflow.admin.service.ModuleService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;
    private final ExecutorRegistryService executorRegistryService;

    @GetMapping
    public Result<List<ModuleVO>> listAll() {
        return Result.success(moduleService.listAll());
    }

    @GetMapping("/{id}")
    public Result<ModuleVO> getById(@PathVariable Long id) {
        return Result.success(moduleService.getById(id));
    }

    @PostMapping
    public Result<ModuleVO> create(@Valid @RequestBody ModuleCreateDTO dto) {
        return Result.success(moduleService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<ModuleVO> update(@PathVariable Long id, @Valid @RequestBody ModuleUpdateDTO dto) {
        return Result.success(moduleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        moduleService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/executors")
    public Result<List<ExecutorRegistryVO>> listExecutors(@PathVariable Long id) {
        return Result.success(executorRegistryService.listByModuleId(id));
    }

    @PutMapping("/executors/{executorId}/status")
    public Result<Void> updateExecutorStatus(@PathVariable Long executorId, @RequestBody Map<String, Integer> body) {
        executorRegistryService.updateStatus(executorId, body.get("status"));
        return Result.success();
    }
}
