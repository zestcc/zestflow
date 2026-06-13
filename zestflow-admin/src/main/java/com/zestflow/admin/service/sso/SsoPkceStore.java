package com.zestflow.admin.service.sso;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * PKCE state 与 code_verifier 临时存储。
 */
@Component
@RequiredArgsConstructor
public class SsoPkceStore {

    private static final String PREFIX = "zestflow:sso:pkce:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public void save(String state, String codeVerifier) {
        redisTemplate.opsForValue().set(PREFIX + state, codeVerifier, TTL.toSeconds(), TimeUnit.SECONDS);
    }

    public String consume(String state) {
        String key = PREFIX + state;
        String verifier = redisTemplate.opsForValue().get(key);
        if (verifier != null) {
            redisTemplate.delete(key);
        }
        return verifier;
    }
}
