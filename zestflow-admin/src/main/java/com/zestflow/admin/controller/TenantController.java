package com.zestflow.admin.controller;

import com.zestflow.admin.model.dto.TenantCreateDTO;
import com.zestflow.admin.model.dto.TenantUpdateDTO;
import com.zestflow.admin.model.vo.TenantVO;
import com.zestflow.admin.service.TenantService;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    public Result<List<TenantVO>> listAll() {
        return Result.success(tenantService.listAll());
    }

    @GetMapping("/{id}")
    public Result<TenantVO> getById(@PathVariable Long id) {
        return Result.success(tenantService.getById(id));
    }

    @PostMapping
    public Result<TenantVO> create(@Valid @RequestBody TenantCreateDTO dto, Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        log.info("创建租户 userId={} name={}", userId, dto.getName());
        return Result.success(tenantService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<TenantVO> update(@PathVariable Long id, @Valid @RequestBody TenantUpdateDTO dto, Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        log.info("更新租户 userId={} tenantId={}", userId, id);
        return Result.success(tenantService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, Authentication authentication) {
        Long userId = SecurityUtils.getUserId(authentication);
        log.info("删除租户 userId={} tenantId={}", userId, id);
        tenantService.delete(id);
        return Result.success();
    }
}
