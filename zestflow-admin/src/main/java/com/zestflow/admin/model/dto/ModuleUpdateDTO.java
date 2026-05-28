package com.zestflow.admin.model.dto;

import lombok.Data;

@Data
public class ModuleUpdateDTO {

    private String name;

    private String description;

    private Integer status;

    private String owner;

    private Integer sortOrder;
}
