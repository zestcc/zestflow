package com.zestflow.test.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 通用工作流请求 — 用于构造各类复杂编排场景的参数
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowRequest {

    /** 场景标识：selector/loader/parser/predicate/full-lifecycle/bind-validate/continue-on-error/timeout/async/diamond/w-shape/chain-timeout/10-layers/saga/conditional-skip/all-types/long-50/not-found/bad-script/bad-subchain/negative-retry/huge-timeout/concurrent-register */
    @Builder.Default
    private String scenario = "selector";

    /** 业务参数 */
    private Map<String, Object> params;

    /** 并发数量 */
    @Builder.Default
    private int concurrency = 1;

    /** 用户ID */
    private String userId;

    /** 来源渠道 */
    private String channel;

    /** 金额 */
    private Integer amount;

    /** 状态值 */
    private String status;

    /** 迭代节点数量 */
    @Builder.Default
    private int iterCount = 3;

    /** 链超时（毫秒） */
    private Long chainTimeoutMs;
}
