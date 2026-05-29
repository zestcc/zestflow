package com.zestflow.admin.model.dto;

import lombok.Data;

@Data
public class DesignUpdateDTO {

    private String code;

    private String name;

    private String description;

    private Integer status;

    private String graphData;

    private String designer;
}
