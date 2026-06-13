package com.zestflow.admin.service.sso.config;

import com.zestflow.admin.config.AdminRedisConditions;
import com.zestflow.admin.service.sso.store.InMemorySsoPkceStore;
import com.zestflow.admin.service.sso.store.RedisSsoPkceStore;
import com.zestflow.admin.service.sso.store.SsoPkceStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * PKCE 存储装配 — standalone 内存 / cluster Redis。
 */
@Configuration
public class SsoStoreConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoPkceStore.class)
    SsoPkceStore inMemorySsoPkceStore() {
        return new InMemorySsoPkceStore();
    }

    @Bean
    @Conditional(AdminRedisConditions.InfrastructureRequired.class)
    @ConditionalOnProperty(name = "zestflow.admin.deploy-mode", havingValue = "cluster")
    SsoPkceStore redisSsoPkceStore(StringRedisTemplate redisTemplate) {
        return new RedisSsoPkceStore(redisTemplate);
    }
}
