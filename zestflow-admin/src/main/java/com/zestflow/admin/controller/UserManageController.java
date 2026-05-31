package com.zestflow.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zestflow.admin.model.dto.AssignModuleRoleDTO;
import com.zestflow.admin.model.dto.UserCreateDTO;
import com.zestflow.admin.model.dto.UserUpdateDTO;
import com.zestflow.admin.model.vo.UserManageVO;
import com.zestflow.admin.service.UserManageService;
import com.zestflow.common.model.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserManageController {

    private final UserManageService userManageService;

    @GetMapping
    public Result<?> listAll(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Integer isSuperAdmin,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (page <= 0 || "all".equals(String.valueOf(page))) {
            return Result.success(userManageService.listAll());
        }
        return Result.success(userManageService.listPage(username, email, status, isSuperAdmin, page, size));
    }

    @GetMapping("/{id}")
    public Result<UserManageVO> getById(@PathVariable Long id) {
        return Result.success(userManageService.getById(id));
    }

    @PostMapping
    public Result<UserManageVO> create(@Valid @RequestBody UserCreateDTO dto) {
        return Result.success(userManageService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<UserManageVO> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        return Result.success(userManageService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        userManageService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/reset-password")
    public Result<Map<String, String>> resetPassword(@PathVariable Long id) {
        String generatedPassword = userManageService.resetPassword(id);
        return Result.success(Collections.singletonMap("generatedPassword", generatedPassword));
    }

    @PostMapping("/{id}/module-roles")
    public Result<Void> assignModuleRole(@PathVariable Long id, @Valid @RequestBody AssignModuleRoleDTO dto) {
        dto.setUserId(id);
        userManageService.assignModuleRole(dto);
        return Result.success();
    }

    @DeleteMapping("/{id}/module-roles/{moduleId}")
    public Result<Void> removeModuleRole(@PathVariable Long id, @PathVariable Long moduleId) {
        userManageService.removeModuleRole(id, moduleId);
        return Result.success();
    }
}
