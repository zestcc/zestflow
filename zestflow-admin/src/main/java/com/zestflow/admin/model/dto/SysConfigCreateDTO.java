package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SysConfigCreateDTO {

    @NotBlank(message = "配置键不能为空")
    private String configKey;

    @NotBlank(message = "配置名称不能为空")
    private String configName;

    private String configValue;

    private String valueType;

    private String category;

    private Integer status;

    private Integer sort;

    private String remark;
}
