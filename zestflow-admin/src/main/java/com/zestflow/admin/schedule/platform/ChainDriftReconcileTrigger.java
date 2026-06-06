package com.zestflow.admin.schedule.platform;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "zestflow.admin.reconcile", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChainDriftReconcileTrigger {

    private final PlatformJobRunner platformJobRunner;

    @Scheduled(fixedDelayString = "${zestflow.admin.reconcile.interval-ms:120000}")
    public void chainDriftReconcile() {
        platformJobRunner.runScheduledByKey(PlatformJobKeys.CHAIN_DRIFT_RECONCILE);
    }
}
