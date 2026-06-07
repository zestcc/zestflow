package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DictDataCreateDTO {

    @NotBlank(message = "字典类型编码不能为空")
    private String typeCode;

    private Long parentId;

    private String parentTypeCode;

    private String parentValue;

    @NotBlank(message = "数据标签不能为空")
    private String label;

    @NotBlank(message = "数据值不能为空")
    private String value;

    private Integer sort;

    private Integer status;

    private String tagType;

    private Integer defaultFlag;

    private String remark;

    private String extra;
}
