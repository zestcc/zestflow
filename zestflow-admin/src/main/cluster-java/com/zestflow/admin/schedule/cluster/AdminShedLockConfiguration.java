package com.zestflow.admin.schedule.cluster;

import com.zestflow.admin.runtime.AdminDeployModeConditions;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock — 仅 cluster 构建（{@code -Pcluster}）且 {@code deploy-mode=cluster} 运行时生效。
 */
@Configuration
@Conditional(AdminDeployModeConditions.Cluster.class)
@EnableSchedulerLock(
        defaultLockAtMostFor = "PT5M",
        defaultLockAtLeastFor = "PT10S"
)
public class AdminShedLockConfiguration {

    @Bean
    public LockProvider adminScheduleLockProvider(
            @Qualifier("adminSharedRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory);
    }
}
