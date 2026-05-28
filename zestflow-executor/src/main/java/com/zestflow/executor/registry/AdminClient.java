package com.zestflow.executor.registry;

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

@Slf4j
@RequiredArgsConstructor
public class AdminClient {

    private final RestTemplate restTemplate;
    private final ExecutorProperties properties;

    private static final ParameterizedTypeReference<Result<Void>> RESULT_VOID_TYPE =
            new ParameterizedTypeReference<Result<Void>>() {};

    /**
     * 注册执行器到 Admin
     */
    public boolean register(RegisterDTO dto) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/registry/register";
                HttpEntity<RegisterDTO> entity = new HttpEntity<>(dto, buildHeaders());
                ResponseEntity<Result<Void>> resp = restTemplate.exchange(
                        url, HttpMethod.POST, entity, RESULT_VOID_TYPE);
                if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                    log.info("注册成功 adminUrl={} executorId={}", adminUrl, dto.getExecutorId());
                    return true;
                }
            } catch (Exception e) {
                log.warn("注册失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        log.error("所有 Admin 地址注册均失败 executorId={}", dto.getExecutorId());
        return false;
    }

    /**
     * 发送心跳
     */
    public boolean heartbeat(HeartbeatDTO dto) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/registry/heartbeat";
                HttpEntity<HeartbeatDTO> entity = new HttpEntity<>(dto, buildHeaders());
                ResponseEntity<Result<Void>> resp = restTemplate.exchange(
                        url, HttpMethod.POST, entity, RESULT_VOID_TYPE);
                if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                    return true;
                }
            } catch (Exception e) {
                log.debug("心跳失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        return false;
    }

    /**
     * 注销执行器
     */
    public boolean deregister(String executorId) {
        List<String> adminList = parseAddresses();
        boolean allSuccess = true;
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/registry/" + executorId;
                HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
                ResponseEntity<Result<Void>> resp = restTemplate.exchange(
                        url, HttpMethod.DELETE, entity, RESULT_VOID_TYPE);
                if (resp.getBody() == null || resp.getBody().getCode() != 200) {
                    allSuccess = false;
                }
            } catch (Exception e) {
                log.warn("注销失败 adminUrl={} error={}", adminUrl, e.getMessage());
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
        return headers;
    }

    private List<String> parseAddresses() {
        return List.of(properties.getAdminAddresses().split(","));
    }
}
