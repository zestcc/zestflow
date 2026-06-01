package com.zestflow.executor.server;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 控制器端点信息 — 供 Admin /playground/scenes/available-endpoints 查询
 */
@Data
@AllArgsConstructor
public class EndpointInfo {
    private String className;
    private String methodName;
    private String requestPath;
    private String requestMethod;
    private List<String> parameters;
    private boolean hasRequestBody;
    private String requestBodyType;
    private String requestBodyTemplate;
    private String responseBodyType;
    private String responseBodyTemplate;
    private String requestHeaders;
}
