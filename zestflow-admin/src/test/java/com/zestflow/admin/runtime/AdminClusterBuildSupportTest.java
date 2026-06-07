package com.zestflow.admin.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminClusterBuildSupportTest {

    @Test
    void clusterArtifactReflectsClasspath() {
        boolean clusterOnClasspath = classOnClasspath(
                "com.zestflow.admin.config.cluster.ClusterOfflineMonitor");
        assertThat(AdminClusterBuildSupport.isClusterArtifact()).isEqualTo(clusterOnClasspath);
    }

    private static boolean classOnClasspath(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
