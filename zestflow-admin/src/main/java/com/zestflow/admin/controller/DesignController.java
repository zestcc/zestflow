package com.zestflow.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.DesignBindDTO;
import com.zestflow.admin.model.dto.DesignCreateDTO;
import com.zestflow.admin.model.dto.DesignUpdateDTO;
import com.zestflow.admin.model.vo.ChainVO;
import com.zestflow.admin.model.vo.DesignVO;
import com.zestflow.admin.service.DesignService;
import com.zestflow.common.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/designs")
@RequiredArgsConstructor
public class DesignController {

    private final DesignService designService;

    @GetMapping
    public Result<IPage<DesignVO>> listByModuleId(
            @RequestParam Long moduleId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(designService.listByModuleId(moduleId, keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<DesignVO> getById(@PathVariable Long id) {
        return Result.success(designService.getById(id));
    }

    @PostMapping
    public Result<DesignVO> create(@Valid @RequestBody DesignCreateDTO dto) {
        return Result.success(designService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<DesignVO> update(@PathVariable Long id, @Valid @RequestBody DesignUpdateDTO dto) {
        return Result.success(designService.update(id, dto));
    }

    @PutMapping("/{id}/graph")
    public Result<Void> saveGraph(@PathVariable Long id, @RequestBody Map<String, String> body) {
        designService.saveGraph(id, body.get("graphData"));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        designService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        designService.toggleStatus(id);
        return Result.success();
    }

    @GetMapping("/{id}/bindings")
    public Result<List<ChainVO>> getBindings(@PathVariable Long id) {
        return Result.success(designService.getBindings(id));
    }

    @GetMapping("/{id}/bindable")
    public Result<List<ChainVO>> getBindable(@PathVariable Long id) {
        return Result.success(designService.getBindable(id));
    }

    @PostMapping("/{id}/bindings")
    public Result<Void> bind(@PathVariable Long id, @Valid @RequestBody DesignBindDTO dto) {
        designService.bind(id, dto.getChainId());
        return Result.success();
    }

    @DeleteMapping("/{id}/bindings/{chainId}")
    public Result<Void> unbind(@PathVariable Long id, @PathVariable Long chainId) {
        designService.unbind(id, chainId);
        return Result.success();
    }
}
