package com.zestflow.admin.model.dto;

import lombok.Data;

@Data
public class ScheduleUpdateDTO {

    private String cron;

    private String routeStrategy;

    private String params;

    private String remark;

    private Integer status;
}
