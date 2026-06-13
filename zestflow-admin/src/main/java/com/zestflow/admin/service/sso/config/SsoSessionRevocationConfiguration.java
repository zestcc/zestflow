package com.zestflow.admin.service.sso.config;

import com.zestflow.admin.config.AdminRedisConditions;
import com.zestflow.admin.service.sso.revocation.InMemorySsoSessionRevocationStore;
import com.zestflow.admin.service.sso.revocation.RedisSsoSessionRevocationStore;
import com.zestflow.admin.service.sso.revocation.SsoSessionRevocationStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * SSO 会话吊销存储 — standalone 内存 / cluster Redis。
 */
@Configuration
@ConditionalOnProperty(prefix = "zestflow.sso", name = "enabled", havingValue = "true")
public class SsoSessionRevocationConfiguration {

    @Bean
    @ConditionalOnMissingBean(SsoSessionRevocationStore.class)
    SsoSessionRevocationStore inMemorySsoSessionRevocationStore(
            @Value("${zestflow.jwt.expiration:86400000}") long jwtExpirationMs) {
        return new InMemorySsoSessionRevocationStore(Duration.ofMillis(jwtExpirationMs));
    }

    @Bean
    @Primary
    @Conditional(AdminRedisConditions.InfrastructureRequired.class)
    @ConditionalOnProperty(name = "zestflow.admin.deploy-mode", havingValue = "cluster")
    SsoSessionRevocationStore redisSsoSessionRevocationStore(
            StringRedisTemplate redisTemplate,
            @Value("${zestflow.jwt.expiration:86400000}") long jwtExpirationMs) {
        return new RedisSsoSessionRevocationStore(redisTemplate, Duration.ofMillis(jwtExpirationMs));
    }
}
