package com.zestflow.admin.playground.model.dto;

import lombok.Data;

/**
 * 演示场景创建 DTO
 */
@Data
public class PlaygroundSceneCreateDTO {

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
}
