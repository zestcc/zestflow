package com.zestflow.admin.model.dto;

import lombok.Data;

@Data
public class DictDataUpdateDTO {

    private String label;

    private String value;

    private Integer sort;

    private Integer status;

    private String tagType;

    private Integer defaultFlag;

    private String remark;
}
