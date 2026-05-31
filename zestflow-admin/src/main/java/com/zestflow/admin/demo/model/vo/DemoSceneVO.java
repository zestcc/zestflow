package com.zestflow.admin.demo.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 演示场景 VO
 */
@Data
public class DemoSceneVO {

    private Long id;
    private String sceneCode;
    private String name;
    private String description;
    private String requestPath;
    private String requestMethod;
    private String requestHeaders;
    private String bodyType;
    private String requestBody;
    private String responseExample;
    private String chainCode;
    private Integer rateLimit;
    private String appCode;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
