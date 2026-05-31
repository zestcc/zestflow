package com.zestflow.admin.demo.model.dto;

import lombok.Data;

/**
 * 演示场景更新 DTO
 */
@Data
public class DemoSceneUpdateDTO {

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
}
