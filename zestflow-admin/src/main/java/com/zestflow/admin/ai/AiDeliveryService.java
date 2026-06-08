package com.zestflow.admin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.model.dto.AiComposeChainRequest;
import com.zestflow.admin.ai.model.dto.AiDeliveryValidateRequest;
import com.zestflow.admin.ai.model.vo.AiValidationVO;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.constant.ChainDeliveryLifecycle;
import com.zestflow.common.exception.BizException;
import com.zestflow.mcp.delivery.ChainComposeService;
import com.zestflow.mcp.delivery.DeliveryReport;
import com.zestflow.mcp.delivery.DeliveryValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin Copilot 交付管道 — compose_chain / validate_delivery 代理（对标 MCP 工具）。
 */
@Service
@RequiredArgsConstructor
public class AiDeliveryService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutorValidateClient executorValidateClient;
    private final ChainComposeService composeService = new ChainComposeService();
    private final DeliveryValidator deliveryValidator = new DeliveryValidator();

    public List<Map<String, String>> listPlatformPatterns() {
        return ChainComposeService.listPlatformPatterns();
    }

    public Map<String, Object> composeChain(AiComposeChainRequest request) throws Exception {
        if (!StringUtils.hasText(request.getAppCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "appCode 不能为空");
        }
        if (!StringUtils.hasText(request.getChainCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "chainCode 不能为空");
        }
        ChainComposeService.ComposeResult result = composeService.compose(
                request.getPatternId(),
                request.getChainCode(),
                request.getChainName(),
                request.getComponentBindings());

        Map<String, Object> out = new LinkedHashMap<>(result.payload());
        String chainJson = (String) out.get("chainDefinitionJson");
        if (StringUtils.hasText(chainJson)) {
            AiValidationVO validation = executorValidateClient.validate(request.getAppCode().trim(), chainJson);
            out.put("validateChain", validation);
        }
        out.put("pipeline", List.of(
                "validate_chain",
                "gen_smoke_suite",
                "run_acceptance_suite",
                "validate_delivery(passed=true)"));
        return out;
    }

    public Map<String, Object> validateDelivery(AiDeliveryValidateRequest request) throws Exception {
        if (!StringUtils.hasText(request.getAppCode())) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "appCode 不能为空");
        }
        boolean strict = request.getStrictMode() == null || request.getStrictMode();

        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> nextActions = new ArrayList<>();

        evaluateDesignScope(request, blocking, warnings, nextActions);

        if (StringUtils.hasText(request.getProjectRoot())) {
            Path root = Path.of(request.getProjectRoot()).toAbsolutePath().normalize();
            if (Files.isDirectory(root)) {
                DeliveryReport report = deliveryValidator.validate(root, request.getAppCode().trim(), strict);
                mergeReport(report, blocking, warnings, nextActions);
            } else {
                warnings.add("PROJECT_ROOT_INVALID: 目录不存在 " + root);
            }
        } else {
            warnings.add("PROJECT_ROOT_SKIPPED: 未提供 projectRoot，仅执行设计域校验；完整门禁请用 MCP validate_delivery");
        }

        boolean passed = blocking.isEmpty();
        if (!passed && nextActions.isEmpty()) {
            nextActions.add("compose_chain → validate_chain → validate_delivery");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("passed", passed);
        out.put("strictMode", strict);
        out.put("blocking", blocking);
        out.put("warnings", warnings);
        out.put("next_actions", nextActions);
        out.put("rule", "passed=true 方可向用户声明功能完成");
        return out;
    }

    private void evaluateDesignScope(AiDeliveryValidateRequest request,
                                     List<String> blocking,
                                     List<String> warnings,
                                     List<String> nextActions) {
        if (!StringUtils.hasText(request.getChainData())) {
            blocking.add("CHAIN_DATA_MISSING: 缺少 chainData");
            nextActions.add("compose_chain");
            return;
        }
        try {
            JsonNode root = MAPPER.readTree(request.getChainData());
            String lifecycle = root.path("config").path("lifecycle").asText(ChainDeliveryLifecycle.BOOTSTRAP);
            if (!ChainDeliveryLifecycle.isProduction(lifecycle)) {
                blocking.add("LIFECYCLE_BOOTSTRAP: chain_data.config.lifecycle 必须为 production");
                nextActions.add("compose_chain（Pattern 模板）");
            }
            int bizNodes = countBusinessNodes(root);
            if (bizNodes < 2) {
                blocking.add("MIN_TASK_NODES: 业务节点数 " + bizNodes + " < 2");
                nextActions.add("compose_chain");
            }
        } catch (Exception e) {
            blocking.add("CHAIN_DATA_PARSE_ERROR: " + e.getMessage());
        }

        AiValidationVO validation = executorValidateClient.validate(
                request.getAppCode().trim(), request.getChainData());
        if (!validation.isValid()) {
            blocking.addAll(validation.getErrors().stream()
                    .map(err -> "VALIDATE_CHAIN: " + err)
                    .toList());
            nextActions.add("validate_chain 修复后重试");
        }
    }

    private static int countBusinessNodes(JsonNode root) {
        JsonNode nodes = root.path("nodes");
        if (!nodes.isArray()) {
            return 0;
        }
        int count = 0;
        for (JsonNode node : nodes) {
            String type = node.path("type").asText("").toUpperCase(Locale.ROOT);
            if (!"START".equals(type) && !"END".equals(type)) {
                count++;
            }
        }
        return count;
    }

    private static void mergeReport(DeliveryReport report,
                                    List<String> blocking,
                                    List<String> warnings,
                                    List<String> nextActions) {
        blocking.addAll(report.blocking());
        warnings.addAll(report.warnings());
        nextActions.addAll(report.nextActions());
    }
}
