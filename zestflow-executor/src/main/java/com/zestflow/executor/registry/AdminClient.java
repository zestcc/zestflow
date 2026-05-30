package com.zestflow.executor.registry;

import com.zestflow.common.constant.RegistryConstants;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainSyncDTO;
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

import java.util.Collections;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class AdminClient {

    private final RestTemplate restTemplate;
    private final ExecutorProperties properties;

    private static final ParameterizedTypeReference<Result<Void>> RESULT_VOID_TYPE =
            new ParameterizedTypeReference<Result<Void>>() {};

    private static final ParameterizedTypeReference<Result<List<String>>> RESULT_LIST_STRING_TYPE =
            new ParameterizedTypeReference<Result<List<String>>>() {};

    private static final ParameterizedTypeReference<Result<ChainDefinitionDTO>> RESULT_CHAIN_DEF_TYPE =
            new ParameterizedTypeReference<Result<ChainDefinitionDTO>>() {};

    // ==================== 注册/心跳 ====================

    public boolean register(RegisterDTO dto) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/register";
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

    public boolean heartbeat(HeartbeatDTO dto) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/heartbeat";
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

    public boolean deregister(String executorId) {
        List<String> adminList = parseAddresses();
        boolean allSuccess = true;
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/registry/" + executorId;
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

    // ==================== 链 API ====================

    /**
     * 获取模块下所有活跃链的 code 列表
     */
    public List<String> fetchActiveChainCodes(String moduleCode) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/chains/active-codes?moduleCode=" + moduleCode;
                HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
                ResponseEntity<Result<List<String>>> resp = restTemplate.exchange(
                        url, HttpMethod.GET, entity, RESULT_LIST_STRING_TYPE);
                if (resp.getBody() != null && resp.getBody().getCode() == 200 && resp.getBody().getData() != null) {
                    return resp.getBody().getData();
                }
            } catch (Exception e) {
                log.warn("获取活跃链列表失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
        return Collections.emptyList();
    }

    /**
     * 获取单个链完整定义
     */
    public ChainDefinitionDTO fetchChainDefinition(String code) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/chains/code/" + code;
                HttpEntity<Void> entity = new HttpEntity<>(buildHeaders());
                ResponseEntity<Result<ChainDefinitionDTO>> resp = restTemplate.exchange(
                        url, HttpMethod.GET, entity, RESULT_CHAIN_DEF_TYPE);
                if (resp.getBody() != null && resp.getBody().getCode() == 200) {
                    return resp.getBody().getData();
                }
            } catch (Exception e) {
                log.warn("获取链定义失败 code={} adminUrl={} error={}", code, adminUrl, e.getMessage());
            }
        }
        return null;
    }

    /**
     * 通知 Admin 链加载状态
     */
    public void notifyChainSync(ChainSyncDTO sync) {
        List<String> adminList = parseAddresses();
        for (String adminUrl : adminList) {
            try {
                String url = adminUrl + "/api/chains/sync";
                HttpEntity<ChainSyncDTO> entity = new HttpEntity<>(sync, buildHeaders());
                restTemplate.exchange(url, HttpMethod.POST, entity, RESULT_VOID_TYPE);
                return; // 一个成功即可
            } catch (Exception e) {
                log.warn("通知链同步失败 adminUrl={} error={}", adminUrl, e.getMessage());
            }
        }
    }

    // ==================== 内部方法 ====================

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
