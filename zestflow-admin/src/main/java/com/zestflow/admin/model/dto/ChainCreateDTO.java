package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChainCreateDTO {

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @NotNull
    private Long moduleId;

    @Size(max = 500)
    private String description;

    private Integer status;
}
