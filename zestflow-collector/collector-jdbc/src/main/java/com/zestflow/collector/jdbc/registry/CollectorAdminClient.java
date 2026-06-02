package com.zestflow.collector.jdbc.registry;

import com.zestflow.common.constant.RegistryAuthConstants;
import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.HeartbeatDTO;
import com.zestflow.common.model.dto.RegisterDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 采集器 Admin 客户端 — 向 Admin 注册、心跳、下线
 * <p>
 * 对标 executor 端 AdminClient 的设计模式，但用于采集器注册场景。
 */
@Slf4j
@RequiredArgsConstructor
public class CollectorAdminClient {

    private final RestTemplate restTemplate;
    private final CollectorRegistryProperties properties;

    private static final ParameterizedTypeReference<Result<Void>> RESULT_VOID_TYPE =
            new ParameterizedTypeReference<Result<Void>>() {};

    public boolean register(RegisterDTO dto) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/collector/register";
                HttpEntity<RegisterDTO> entity = new HttpEntity<>(dto, buildHeaders());
                ResponseEntity<Result<Void>> resp = restTemplate.exchange(
                        url, HttpMethod.POST, entity, RESULT_VOID_TYPE);
                if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                    log.info("采集器注册成功 adminUrl={} collectorId={}", adminUrl, dto.getExecutorId());
                    return true;
                }
            } catch (Exception e) {
                log.warn("采集器注册失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        log.error("所有 Admin 地址注册均失败 collectorId={}", dto.getExecutorId());
        return false;
    }

    public boolean heartbeat(HeartbeatDTO dto) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/collector/heartbeat";
                HttpEntity<HeartbeatDTO> entity = new HttpEntity<>(dto, buildHeaders());
                ResponseEntity<Result<Void>> resp = restTemplate.exchange(
                        url, HttpMethod.POST, entity, RESULT_VOID_TYPE);
                if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("采集器心跳失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        return false;
    }

    public boolean deregister(String collectorId) {
        List<String> adminList = parseAddresses();
        boolean allSuccess = true;
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/collector/" + collectorId;
                HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
                ResponseEntity<Result<Void>> resp = restTemplate.exchange(
                        url, HttpMethod.DELETE, entity, RESULT_VOID_TYPE);
                if (resp.getBody() == null || resp.getBody().getCode() != 200) {
                    allSuccess = false;
                }
            } catch (Exception e) {
                log.warn("采集器注销失败 adminUrl={} error={}", adminUrl, e.getMessage());
                allSuccess = false;
            }
        }
        return allSuccess;
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (properties.getAccessToken() != null && !properties.getAccessToken().isEmpty()) {
            headers.set("Authorization", "Bearer " + properties.getAccessToken());
        }
        if (properties.getRegistryToken() != null && !properties.getRegistryToken().isEmpty()) {
            headers.set(RegistryAuthConstants.REGISTRY_TOKEN_HEADER, properties.getRegistryToken());
        }
        return headers;
    }

    private List<String> parseAddresses() {
        return List.of(properties.getAdminAddresses().split(","));
    }
}
