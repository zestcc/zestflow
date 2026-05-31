package com.zestflow.admin.demo.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演示执行记录 VO（不含 requestIp）
 */
@Data
public class DemoRecordVO {

    private Long id;
    private Long sceneId;
    private String sceneName;
    private String sceneCode;
    private String requestMethod;
    private String requestPath;
    private String requestHeaders;
    private String bodyType;
    private String requestBody;
    private Integer responseStatus;
    private String responseBody;
    private String responseHeaders;
    private String chainCode;
    private String instanceId;
    private Integer status;
    private Long costMs;
    private String errorMsg;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
