package com.zestflow.admin.playground.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 可用端点 VO — 描述一个 Controller 方法的请求映射信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableEndpointVO {

    /** Controller 类名（不含包路径） */
    private String className;
    /** 方法名 */
    private String methodName;
    /** 请求路径 */
    private String requestPath;
    /** 请求方法（GET/POST/PUT/DELETE） */
    private String requestMethod;
    /** 参数列表（名称:类型） */
    private List<String> parameters;
    /** 是否有 @RequestBody */
    private boolean hasRequestBody;
    /** @RequestBody 参数的 DTO 类名（简名） */
    private String requestBodyType;
    /** 自动生成的请求体示例 JSON */
    private String requestBodyTemplate;
    /** 响应体数据类型（简名） */
    private String responseBodyType;
    /** 自动生成的响应体示例 JSON */
    private String responseBodyTemplate;
    /** 请求头信息 */
    private String requestHeaders;
}
