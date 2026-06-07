package com.zestflow.executor.schedule.external;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.zestflow.executor.http.ChainExecuteFacade;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * xxl-job Handler — 将调度回调转为链进程内执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "zestflow.executor.schedule", name = "driver", havingValue = "external")
public class XxlJobChainJobHandler {

    private final ChainExecuteFacade chainExecuteFacade;
    private final XxlJobScheduleProperties properties;

    @XxlJob("zestflowChainJob")
    public void executeDefault() {
        executeChain(XxlJobHelper.getJobParam());
    }

    public void executeChain(String chainCode) {
        if (chainCode == null || chainCode.isBlank()) {
            XxlJobHelper.handleFail("jobParam 须为 chainCode");
            return;
        }
        String trimmed = chainCode.trim();
        try {
            ChainExecuteRequestDTO req = ChainExecuteRequestDTO.builder()
                    .chainCode(trimmed)
                    .source("xxl-job")
                    .idempotencyKey("xxl-" + XxlJobHelper.getJobId() + "-" + System.currentTimeMillis())
                    .build();
            chainExecuteFacade.executeCore(req);
            XxlJobHelper.handleSuccess("chain=" + trimmed);
        } catch (Exception e) {
            log.error("xxl-job 链执行失败 chainCode={}", trimmed, e);
            XxlJobHelper.handleFail(e.getMessage());
        }
    }

    public String defaultHandlerName() {
        return properties.getDefaultJobHandler();
    }
}
