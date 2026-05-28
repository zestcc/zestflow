package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignModuleRoleDTO {

    @NotNull
    private Long userId;

    @NotNull
    private Long moduleId;

    @NotNull
    private Long roleId;
}
