package com.zestflow.admin.controller;

import com.zestflow.admin.model.vo.RoleVO;
import com.zestflow.admin.service.RoleService;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public Result<List<RoleVO>> listAll() {
        return Result.success(roleService.listAll());
    }
}
