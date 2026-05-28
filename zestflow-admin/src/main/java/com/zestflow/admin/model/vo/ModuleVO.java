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
public class ModuleVO {

    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private String owner;
    private Integer sortOrder;
    private Integer retryCount;
    private Integer retryInterval;
    private Integer executorTotal;
    private Integer executorHealthy;
    private Integer executorError;
    private Integer executorOffline;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
