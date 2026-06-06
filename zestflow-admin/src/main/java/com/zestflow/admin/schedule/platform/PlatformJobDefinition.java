package com.zestflow.admin.schedule.platform;

import lombok.Builder;
import lombok.Value;

/**
 * 平台任务元数据 — 启动时同步至 schedule 表供调度中心展示。
 */
@Value
@Builder
public class PlatformJobDefinition {

    String jobKey;
    String name;
    String module;
    String scheduleKind;
    Long fixedIntervalMs;
    String cron;
    String remark;
    boolean editable;
    /** 节点本地执行，Admin 仅登记元数据、不写执行日志 */
    boolean remote;
}
