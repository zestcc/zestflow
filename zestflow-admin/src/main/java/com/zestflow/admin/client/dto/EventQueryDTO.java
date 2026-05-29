package com.zestflow.admin.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 事件查询请求 DTO — 与 Collector 端 EventQuery 结构一致
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventQueryDTO {

    /** 链实例 ID */
    private String chainId;

    /** 执行器 ID */
    private String executorId;

    /** 应用名 */
    private String appName;

    /** 事件类型列表 */
    private List<String> eventTypes;

    /** 开始时间（毫秒） */
    private Long startTime;

    /** 结束时间（毫秒） */
    private Long endTime;

    /** 状态（0=失败, 1=成功） */
    private Integer status;

    /** 关键字 */
    private String keyword;

    /** 页码 */
    private int page = 1;

    /** 每页条数 */
    private int pageSize = 20;
}
