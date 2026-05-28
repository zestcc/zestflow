package com.zestflow.admin.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsVO {

    private long totalModules;
    private long totalExecutors;
    private long healthyExecutors;
    private long errorExecutors;
    private long offlineExecutors;
}
