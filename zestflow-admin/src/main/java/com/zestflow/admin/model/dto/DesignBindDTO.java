package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DesignBindDTO {

    @NotNull
    private Long chainId;
}
