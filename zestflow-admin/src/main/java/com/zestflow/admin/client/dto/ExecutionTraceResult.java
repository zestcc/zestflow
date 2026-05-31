package com.zestflow.admin.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 执行轨迹结果 DTO — 映射 Collector 端返回的 ExecutionTrace
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionTraceResult {

    private int code;
    private String message;
    private Map<String, Object> data;
}
