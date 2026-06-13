package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 在线 Executor 对等节点（注册表查询结果，供调度路由 / Failover 使用）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeerExecutorDTO {

    private String executorId;

    private String appCode;

    private String host;

    private int port;
}
