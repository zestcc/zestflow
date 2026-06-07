package com.zestflow.admin.model.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SysConfigVO {

    private Long id;
    private String configKey;
    private String configName;
    private String configValue;
    private String valueType;
    private String category;
    private Integer status;
    private Integer sort;
    private String remark;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
