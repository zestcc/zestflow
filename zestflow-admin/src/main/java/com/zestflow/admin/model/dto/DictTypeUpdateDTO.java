package com.zestflow.admin.model.dto;

import lombok.Data;

@Data
public class DictTypeUpdateDTO {

    private String name;

    private String description;

    private Integer status;

    private Integer sort;
}
