package com.zestflow.admin.playground.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演示执行记录 VO（不含 requestIp）
 */
@Data
public class PlaygroundRecordVO {

    private Long id;
    private Long sceneId;
    private String sceneName;
    private String sceneCode;
    private String requestMethod;
    private String requestPath;
    private String requestHeaders;
    private String bodyType;
    /** 调用载荷 ID */
    private String invocationId;
    /** 详情页按需从 app_log 加载 */
    private String requestBody;
    private Integer responseStatus;
    /** 详情页按需从 app_log 加载 */
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
