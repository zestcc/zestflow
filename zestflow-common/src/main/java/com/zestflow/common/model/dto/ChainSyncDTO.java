package com.zestflow.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Executor 向 Admin 同步链加载状态 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChainSyncDTO {

    /** 执行器标识 */
    private String executorId;

    /** 已加载的链编码列表 */
    private List<String> loadedChains;

    /** 同步状态：LOADING / READY / FAILED */
    private String status;

    /** 错误信息（仅 FAILED 时） */
    private String errorMessage;

    /** 同步时间戳 */
    private Long timestamp;
}
