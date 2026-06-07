package com.zestflow.admin.tenant;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantCloneSummary {

    @Builder.Default
    private final int roles = 0;
    @Builder.Default
    private final int dictTypes = 0;
    @Builder.Default
    private final int dictData = 0;
    @Builder.Default
    private final int playgroundScenes = 0;
    @Builder.Default
    private final int schedules = 0;
    @Builder.Default
    private final int sysConfigs = 0;

    public static TenantCloneSummary empty() {
        return TenantCloneSummary.builder().build();
    }

    public int totalItems() {
        return roles + dictTypes + dictData + playgroundScenes + schedules + sysConfigs;
    }
}
