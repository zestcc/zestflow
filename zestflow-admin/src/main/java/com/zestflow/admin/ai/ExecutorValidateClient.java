package com.zestflow.admin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.client.ExecutorProxyService;
import com.zestflow.admin.ai.model.vo.AiValidationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 通过 ExecutorProxyService 调用 ChainValidator
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExecutorValidateClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String VALIDATE_PATH = "/api/chains/validate-definition";

    private final ExecutorProxyService executorProxyService;

    public AiValidationVO validate(String appCode, String chainData) {
        if (!StringUtils.hasText(appCode)) {
            return AiValidationVO.builder()
                    .valid(false)
                    .errors(List.of("appCode 不能为空"))
                    .build();
        }
        String baseUrl = executorProxyService.resolveExecutorBaseUrl(appCode);
        if (baseUrl == null) {
            return AiValidationVO.builder()
                    .valid(false)
                    .errors(List.of("无可用执行器"))
                    .build();
        }
        String body = buildValidateRequestBody(chainData);
        ExecutorProxyService.ExecutorResult result = executorProxyService.executeOnExecutorUrl(
                baseUrl, "POST", VALIDATE_PATH, body);
        return parseValidationResult(result);
    }

    private String buildValidateRequestBody(String chainData) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode root = MAPPER.createObjectNode();
            String code = "AI_VALIDATE";
            Integer version = 1;
            if (StringUtils.hasText(chainData)) {
                JsonNode chain = MAPPER.readTree(chainData);
                if (chain.has("code") && !chain.get("code").isNull()) {
                    code = chain.get("code").asText(code);
                }
                if (chain.has("version") && !chain.get("version").isNull()) {
                    version = chain.get("version").asInt(1);
                }
                root.put("chainCode", code);
                root.put("version", version);
                root.put("chainData", chainData);
            } else {
                root.put("chainCode", code);
                root.put("chainData", "{}");
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("构建校验请求体失败", e);
            return "{\"chainCode\":\"AI_VALIDATE\",\"chainData\":" + (chainData != null ? chainData : "{}") + "}";
        }
    }

    AiValidationVO parseValidationResult(ExecutorProxyService.ExecutorResult result) {
        if (result == null || !result.isOk() || !StringUtils.hasText(result.getResponseBody())) {
            String msg = result != null && StringUtils.hasText(result.getMessage())
                    ? result.getMessage() : "校验请求失败";
            return AiValidationVO.builder().valid(false).errors(List.of(msg)).build();
        }
        try {
            JsonNode root = MAPPER.readTree(result.getResponseBody());
            JsonNode data = root.has("data") ? root.get("data") : root;
            boolean valid = data.has("valid") && data.get("valid").asBoolean(false);
            List<String> errors = new ArrayList<>();
            if (data.has("errors") && data.get("errors").isArray()) {
                for (JsonNode err : data.get("errors")) {
                    errors.add(err.asText());
                }
            }
            return AiValidationVO.builder().valid(valid).errors(errors).build();
        } catch (Exception e) {
            log.warn("解析校验响应失败", e);
            return AiValidationVO.builder()
                    .valid(false)
                    .errors(List.of("解析校验响应失败"))
                    .build();
        }
    }
}
