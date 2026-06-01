package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TenantCreateDTO {

    @NotBlank(message = "租户名称不能为空")
    private String name;

    @NotBlank(message = "租户编码不能为空")
    private String code;

    private String description;
}
