package com.zestflow.executor.design;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignPO {
    private String code;
    private String name;
    private String description;
    private String designer;
    private Integer status;
    private String graphData;
    private String chainData;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
    private Integer isDeleted;
}
