package com.zestflow.admin.playground.model.dto;

import lombok.Data;

/**
 * 演示记录查询 DTO
 */
@Data
public class PlaygroundRecordQueryDTO {

    private Long sceneId;
    private String sceneCode;
    private String sceneName;
    private String chainCode;
    private Integer status;
    private String keyword;
    private String appCode;
    private String startTime;
    private String endTime;
    private int page = 1;
    private int size = 20;
}
