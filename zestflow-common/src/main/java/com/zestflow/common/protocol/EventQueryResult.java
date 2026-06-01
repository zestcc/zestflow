package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 事件查询结果 — 单条事件记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EventQueryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;
    private String eventType;
    private String executionId;
    private String chainId;
    private String chainName;
    private String nodeId;
    private String nodeName;
    private String executorId;
    private String appName;
    private String params;
    private String result;
    private String errorMessage;
    private Long costMs;
    private Integer status;
    private Long timestamp;
    private String metadata;
    private String createTime;
}
