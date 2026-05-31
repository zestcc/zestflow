package com.zestflow.executor.chain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainPO {
    private String code;
    private String name;
    private String description;
    private Integer status;
    private String designCode;
    private Integer version;
    private Long tenantId;
    private String appCode;
    private String createdBy;
    private String updatedBy;
    private String createdAt;
    private String updatedAt;
    private Integer isDeleted;
}
