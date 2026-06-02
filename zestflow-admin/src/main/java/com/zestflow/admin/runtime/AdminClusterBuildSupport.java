package com.zestflow.admin.runtime;

/**
 * 检测 Admin 是否以 {@code mvn -Pcluster} 构建（ShedLock 与集群调度入口仅在 cluster 构建中打包）。
 */
public final class AdminClusterBuildSupport {

    private static final String CLUSTER_SCHEDULE_MONITOR =
            "com.zestflow.admin.schedule.cluster.ClusterScheduleMonitor";

    private AdminClusterBuildSupport() {
    }

    public static boolean isClusterArtifact() {
        try {
            Class.forName(CLUSTER_SCHEDULE_MONITOR);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
