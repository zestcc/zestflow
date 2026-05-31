package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DictTypeCreateDTO {

    @NotBlank(message = "字典编码不能为空")
    private String code;

    @NotBlank(message = "字典名称不能为空")
    private String name;

    private String description;

    private Integer status;

    private Integer sort;
}
