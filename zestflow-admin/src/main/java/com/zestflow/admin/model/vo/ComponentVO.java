package com.zestflow.admin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentVO {

    private Long id;
    private String executorId;
    private String componentId;
    private String componentName;
    private String description;
    private String groupName;
    private Long timeout;
    private Boolean async;
    private String appCode;
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
