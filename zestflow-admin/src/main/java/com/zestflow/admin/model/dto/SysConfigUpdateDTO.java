package com.zestflow.admin.model.dto;

import lombok.Data;

@Data
public class SysConfigUpdateDTO {

    private String configName;
    private String configValue;
    private String valueType;
    private String category;
    private Integer status;
    private Integer sort;
    private String remark;
}
