package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class ModuleCreateDTO {

    @NotBlank
    @Size(min = 2, max = 50)
    private String code;

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    private Integer status;

    @Size(max = 50)
    private String owner;

    private Integer sortOrder;
}
