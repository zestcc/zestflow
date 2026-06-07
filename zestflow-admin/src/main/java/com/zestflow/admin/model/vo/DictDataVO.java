package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DictDataVO {

    private Long id;
    private String typeCode;
    private Long parentId;
    private String parentTypeCode;
    private String parentValue;
    private String label;
    private String value;
    private Integer sort;
    private Integer status;
    private String tagType;
    private Integer defaultFlag;
    private String remark;
    private String extra;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
