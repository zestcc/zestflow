package com.zestflow.admin.tenant;

import com.zestflow.admin.model.entity.TenantIpMappingPO;
import com.zestflow.admin.model.entity.TenantPO;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TenantProvisionResult {

    private final TenantPO tenant;
    private final TenantIpMappingPO ipMapping;
    private final TenantCloneSummary cloneSummary;

    /** 兼容旧字段：克隆的演示场景数 */
    public int getScenesCloned() {
        return cloneSummary != null ? cloneSummary.getPlaygroundScenes() : 0;
    }

    public int getItemsCloned() {
        return cloneSummary != null ? cloneSummary.totalItems() : 0;
    }
}
