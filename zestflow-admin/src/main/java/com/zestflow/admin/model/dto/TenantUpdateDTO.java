package com.zestflow.admin.model.dto;

import lombok.Data;

@Data
public class TenantUpdateDTO {

    private String name;

    private String description;

    private Integer status;
}
