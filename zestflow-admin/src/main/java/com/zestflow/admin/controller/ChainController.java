package com.zestflow.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.ChainCreateDTO;
import com.zestflow.admin.model.dto.ChainUpdateDTO;
import com.zestflow.admin.model.vo.ChainVO;
import com.zestflow.admin.service.ChainService;
import com.zestflow.common.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chains")
@RequiredArgsConstructor
public class ChainController {

    private final ChainService chainService;

    @GetMapping
    public Result<IPage<ChainVO>> listByModuleId(
            @RequestParam Long moduleId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(chainService.listByModuleId(moduleId, keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public Result<ChainVO> getById(@PathVariable Long id) {
        return Result.success(chainService.getById(id));
    }

    @PostMapping
    public Result<ChainVO> create(@Valid @RequestBody ChainCreateDTO dto) {
        return Result.success(chainService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<ChainVO> update(@PathVariable Long id, @Valid @RequestBody ChainUpdateDTO dto) {
        return Result.success(chainService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        chainService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        chainService.toggleStatus(id);
        return Result.success();
    }
}
