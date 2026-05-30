package com.zestflow.admin.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScheduleCreateDTO {

    @NotNull(message = "模块ID不能为空")
    private Long moduleId;

    @NotBlank(message = "链编码不能为空")
    private String chainCode;

    @NotBlank(message = "链名称不能为空")
    private String chainName;

    @NotBlank(message = "cron 表达式不能为空")
    private String cron;

    private String routeStrategy;

    private String params;

    private String remark;
}
