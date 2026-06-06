package com.zestflow.common.protocol;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 失败错误聚类 — 按错误信息前缀分组
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailureClusterItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 错误摘要（截断后的前缀） */
    private String errorSummary;

    /** 出现次数 */
    private long count;

    /** 最近发生时间（毫秒） */
    private long lastSeen;
}
