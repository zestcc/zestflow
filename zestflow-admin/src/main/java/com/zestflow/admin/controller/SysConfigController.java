package com.zestflow.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.SysConfigCreateDTO;
import com.zestflow.admin.model.dto.SysConfigUpdateDTO;
import com.zestflow.admin.model.vo.SysConfigVO;
import com.zestflow.admin.service.SysConfigService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sys-configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    @GetMapping
    public Result<IPage<SysConfigVO>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(sysConfigService.list(keyword, category, status, page, size));
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(sysConfigService.listCategories());
    }

    @GetMapping("/{id}")
    public Result<SysConfigVO> getById(@PathVariable Long id) {
        return Result.success(sysConfigService.getById(id));
    }

    @PostMapping
    public Result<SysConfigVO> create(@Valid @RequestBody SysConfigCreateDTO dto) {
        String username = SecurityUtils.getCurrentUsername();
        return Result.success(sysConfigService.create(dto, username));
    }

    @PutMapping("/{id}")
    public Result<SysConfigVO> update(@PathVariable Long id, @Valid @RequestBody SysConfigUpdateDTO dto) {
        return Result.success(sysConfigService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysConfigService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> toggleStatus(@PathVariable Long id) {
        sysConfigService.toggleStatus(id);
        return Result.success();
    }
}
