package com.zestflow.admin.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.TenantAiConfigService.EffectiveAiConfig;
import com.zestflow.admin.ai.model.dto.*;
import com.zestflow.admin.ai.model.entity.AiCopilotMessagePO;
import com.zestflow.admin.ai.model.entity.AiCopilotSessionPO;
import com.zestflow.admin.ai.model.vo.*;
import com.zestflow.admin.ai.repository.AiCopilotMessageMapper;
import com.zestflow.admin.ai.repository.AiCopilotSessionMapper;
import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.protocol.ExecutionTrace;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * AI Copilot 核心业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCopilotService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern PHONE_PATTERN = Pattern.compile("1[3-9]\\d{9}");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\\d{17}[\\dXx]");
    private static final int SUMMARY_MAX_LEN = 2000;

    private final AiProperties aiProperties;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiChatClient aiChatClient;
    private final PromptBuilder promptBuilder;
    private final ExecutorValidateClient executorValidateClient;
    private final AiComponentScaffoldBuilder scaffoldBuilder;
    private final CollectorQueryAggregator collectorQueryAggregator;
    private final AiRagService aiRagService;
    private final AiCopilotSessionMapper sessionMapper;
    private final AiCopilotMessageMapper messageMapper;

    public AiExplainResponse explain(AiExplainRequest request) {
        requireCopilotEnabled();
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        EffectiveAiConfig config = tenantAiConfigService.resolveEffectiveConfig(tenantId);

        String chainData = maskIfNeeded(request.getCurrentChainData());
        String system = promptBuilder.buildSystemPrompt("explain", request.getAllowedComponents());
        String user = promptBuilder.buildUserPrompt("explain", "解释当前链", chainData, null);

        Long sessionId = recordSession(tenantId, request.getAppCode(), request.getDesignId(),
                request.getChainCode(), "explain");
        long startMs = System.currentTimeMillis();
        try {
            String reply = chat(config, system, user, false, tenantId, request.getAppCode());
            recordMessage(sessionId, tenantId, "user", "解释当前链");
            recordMessage(sessionId, tenantId, "assistant", truncate(reply));
            finalizeSession(sessionId, startMs, true, null);
            return AiExplainResponse.builder()
                    .explanation(reply)
                    .sessionId(sessionId)
                    .build();
        } catch (RuntimeException e) {
            finalizeSession(sessionId, startMs, false, e.getMessage());
            throw e;
        }
    }

    public AiSuggestResponse suggest(AiSuggestRequest request) {
        requireCopilotEnabled();
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        EffectiveAiConfig config = tenantAiConfigService.resolveEffectiveConfig(tenantId);

        String mode = StringUtils.hasText(request.getMode()) ? request.getMode() : "generate";
        String chainData = maskIfNeeded(request.getCurrentChainData());
        String system = promptBuilder.buildSystemPrompt(mode, request.getAllowedComponents());
        String user = promptBuilder.buildUserPrompt(mode, request.getUserMessage(), chainData, null);

        Long sessionId = recordSession(tenantId, request.getAppCode(), request.getDesignId(),
                request.getChainCode(), "suggest");
        recordMessage(sessionId, tenantId, "user", truncate(request.getUserMessage()));
        long startMs = System.currentTimeMillis();
        try {
            String llmReply = chat(config, system, user, true, tenantId, request.getAppCode());
            ParsedChainProposal proposal = parseChainProposal(llmReply);

            AiValidationVO validation = executorValidateClient.validate(
                    request.getAppCode(), proposal.chainData());
            int repairRounds = 0;

            while (!validation.isValid() && repairRounds < aiProperties.getRepairMaxRounds()) {
                repairRounds++;
                String fixSystem = promptBuilder.buildSystemPrompt("fix-errors", request.getAllowedComponents());
                String fixUser = promptBuilder.buildUserPrompt("fix-errors", request.getUserMessage(),
                        proposal.chainData(), validation.getErrors());
                llmReply = chat(config, fixSystem, fixUser, true, tenantId, request.getAppCode());
                proposal = parseChainProposal(llmReply);
                validation = executorValidateClient.validate(request.getAppCode(), proposal.chainData());
            }

            recordMessage(sessionId, tenantId, "assistant", truncate(proposal.summary()));
            finalizeSession(sessionId, startMs, true, null);

            return AiSuggestResponse.builder()
                    .proposedChainData(proposal.chainData())
                    .summary(proposal.summary())
                    .validation(validation)
                    .sessionId(sessionId)
                    .repairRounds(repairRounds)
                    .build();
        } catch (RuntimeException e) {
            finalizeSession(sessionId, startMs, false, e.getMessage());
            throw e;
        }
    }

    public AiValidationVO validate(AiValidateRequest request) {
        return executorValidateClient.validate(request.getAppCode(), request.getChainData());
    }

    public AiExpressionSuggestResponse expressionSuggest(AiExpressionSuggestRequest request) {
        requireCopilotEnabled();
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        EffectiveAiConfig config = tenantAiConfigService.resolveEffectiveConfig(tenantId);

        String system = promptBuilder.buildSystemPrompt("expression", request.getAllowedComponents());
        String user = promptBuilder.buildExpressionUserPrompt(
                request.getUserMessage(), request.getCurrentExpression(), request.getContextHint());

        Long sessionId = recordSession(tenantId, request.getAppCode(), request.getDesignId(),
                request.getChainCode(), "expression");
        recordMessage(sessionId, tenantId, "user", truncate(request.getUserMessage()));
        long startMs = System.currentTimeMillis();
        try {
            String reply = chat(config, system, user, true, tenantId, request.getAppCode());
            ParsedExpression parsed = parseExpression(reply);
            recordMessage(sessionId, tenantId, "assistant", truncate(parsed.expression()));
            finalizeSession(sessionId, startMs, true, null);
            return AiExpressionSuggestResponse.builder()
                    .expression(parsed.expression())
                    .explanation(parsed.explanation())
                    .sessionId(sessionId)
                    .build();
        } catch (RuntimeException e) {
            finalizeSession(sessionId, startMs, false, e.getMessage());
            throw e;
        }
    }

    public AiComponentScaffoldResponse componentScaffold(AiComponentScaffoldRequest request) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        AiComponentScaffoldBuilder.ScaffoldDefinition def = scaffoldBuilder.fromRequest(request);
        String javaCode = scaffoldBuilder.generateJavaClass(def);
        String summary = "已生成 @ZestComponent(" + def.groupName() + ") + "
                + annotationLabel(def.componentType()) + "(" + def.componentId() + ") 骨架";

        Long sessionId = recordSession(tenantId, request.getAppCode(), null, null, "scaffold");
        recordMessage(sessionId, tenantId, "user", truncate(request.getDescription()));
        recordMessage(sessionId, tenantId, "assistant", truncate(summary));

        return AiComponentScaffoldResponse.builder()
                .fullJavaCode(javaCode)
                .summary(summary)
                .checklist(scaffoldBuilder.buildChecklist(request.getComponentId()))
                .sessionId(sessionId)
                .build();
    }

    public AiDiagnoseResponse diagnose(AiDiagnoseRequest request) {
        requireCopilotEnabled();
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        EffectiveAiConfig config = tenantAiConfigService.resolveEffectiveConfig(tenantId);

        String executionId = resolveExecutionId(request);
        ExecutionTrace trace = fetchTrace(executionId, request.getAppCode());
        String errorSummary = buildErrorSummary(request, trace);
        String traceSummary = summarizeTrace(trace);

        Long sessionId = recordSession(tenantId, request.getAppCode(), request.getDesignId(),
                request.getChainCode(), "diagnose");
        recordMessage(sessionId, tenantId, "user", truncate(errorSummary));
        long startMs = System.currentTimeMillis();

        try {
            String system = promptBuilder.buildSystemPrompt("diagnose", null);
            String user = promptBuilder.buildDiagnoseUserPrompt(errorSummary, traceSummary);
            String reply = chat(config, system, user, true, tenantId, request.getAppCode());
            ParsedDiagnosis parsed = parseDiagnosis(reply);
            recordMessage(sessionId, tenantId, "assistant", truncate(parsed.diagnosis()));
            finalizeSession(sessionId, startMs, true, null);

            return AiDiagnoseResponse.builder()
                    .diagnosis(parsed.diagnosis())
                    .suggestion(parsed.suggestion())
                    .stub(false)
                    .sessionId(sessionId)
                    .openDesignPath(buildOpenDesignPath(request))
                    .build();
        } catch (Exception e) {
            log.warn("AI 日志诊断失败 executionId={}", executionId, e);
            finalizeSession(sessionId, startMs, false, e.getMessage());
            String fallbackDiagnosis = StringUtils.hasText(trace != null ? trace.getErrorMessage() : null)
                    ? trace.getErrorMessage()
                    : errorSummary;
            return AiDiagnoseResponse.builder()
                    .diagnosis(fallbackDiagnosis != null ? fallbackDiagnosis : "未能获取详细错误信息")
                    .suggestion("请在设计器中使用 Copilot「按错误修复」，或检查失败节点的入参与表达式")
                    .stub(true)
                    .sessionId(sessionId)
                    .openDesignPath(buildOpenDesignPath(request))
                    .build();
        }
    }

    public AiTestConnectionResponse testConnection(AiTestConnectionRequest request) {
        AiTenantConfigSaveDTO override = new AiTenantConfigSaveDTO();
        override.setPreset(request.getPreset());
        override.setBaseUrl(request.getBaseUrl());
        override.setApiKey(request.getApiKey());
        override.setModel(request.getModel());

        EffectiveAiConfig config = tenantAiConfigService.resolveForTest(override);
        if (!config.ready()) {
            return AiTestConnectionResponse.builder()
                    .success(false)
                    .message("配置不完整：请填写 baseUrl、model" + (config.apiKeyRequired() ? " 与 API Key" : ""))
                    .build();
        }

        long start = System.currentTimeMillis();
        try {
            AiChatClient.AiChatOptions options = buildOptions(config);
            String reply = aiChatClient.chat(
                    List.of(new AiChatClient.ChatMessage("user", "Reply with exactly: OK")),
                    options);
            long latency = System.currentTimeMillis() - start;
            boolean ok = reply != null && reply.toUpperCase().contains("OK");
            return AiTestConnectionResponse.builder()
                    .success(ok)
                    .latencyMs(latency)
                    .model(config.model())
                    .message(ok ? "OK" : "收到响应但内容异常")
                    .build();
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("测试连接失败 preset={}", request.getPreset(), e);
            return AiTestConnectionResponse.builder()
                    .success(false)
                    .latencyMs(latency)
                    .model(config.model())
                    .message("连接失败")
                    .build();
        }
    }

    public void recordFeedback(Long sessionId, AiSessionFeedbackDTO dto) {
        AiCopilotSessionPO session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        if (!tenantId.equals(session.getTenantId())) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        session.setAdopted(dto.getAdopted());
        sessionMapper.updateById(session);
    }

    private void requireCopilotEnabled() {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        if (!tenantAiConfigService.isCopilotEnabledForTenant(tenantId)) {
            throw new BizException(ErrorCode.AI_COPILOT_DISABLED);
        }
    }

    private String chat(EffectiveAiConfig config, String system, String user, boolean jsonMode,
                        Long tenantId, String appCode) {
        List<AiChatClient.ChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatClient.ChatMessage("system", enrichSystemWithRag(system, user, tenantId, appCode)));
        messages.add(new AiChatClient.ChatMessage("user", user));
        return aiChatClient.chat(messages, buildOptions(config, jsonMode));
    }

    private String enrichSystemWithRag(String system, String userQuery, Long tenantId, String appCode) {
        if (!aiProperties.isRagEnabled()) {
            return system;
        }
        List<String> snippets = aiRagService.retrieve(tenantId, appCode, userQuery, aiProperties.getRagMaxChunks());
        if (snippets.isEmpty()) {
            return system;
        }
        StringBuilder sb = new StringBuilder(system);
        sb.append("\n\n参考知识库片段（请优先遵循）：\n");
        for (String snippet : snippets) {
            sb.append("---\n").append(snippet).append('\n');
        }
        return sb.toString();
    }

    private void finalizeSession(Long sessionId, long startMs, boolean success, String errorMessage) {
        if (sessionId == null) {
            return;
        }
        AiCopilotSessionPO update = new AiCopilotSessionPO();
        update.setId(sessionId);
        update.setLatencyMs((int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startMs));
        update.setSuccess(success ? 1 : 0);
        update.setErrorMessage(truncate(errorMessage));
        sessionMapper.updateById(update);
    }

    private AiChatClient.AiChatOptions buildOptions(EffectiveAiConfig config) {
        return buildOptions(config, false);
    }

    private AiChatClient.AiChatOptions buildOptions(EffectiveAiConfig config, boolean jsonMode) {
        return new AiChatClient.AiChatOptions(
                config.baseUrl(),
                config.apiKey(),
                config.model(),
                aiProperties.getTimeoutMs(),
                aiProperties.getMaxTokens(),
                aiProperties.getTemperature(),
                jsonMode
        );
    }

    private Long recordSession(Long tenantId, String appCode, String designId,
                               String chainCode, String mode) {
        AiCopilotSessionPO session = new AiCopilotSessionPO();
        session.setTenantId(tenantId);
        session.setUserId(currentUserId());
        session.setAppCode(appCode);
        session.setDesignId(designId);
        session.setChainCode(chainCode);
        session.setMode(mode);
        sessionMapper.insert(session);
        return session.getId();
    }

    private void recordMessage(Long sessionId, Long tenantId, String role, String summary) {
        AiCopilotMessagePO msg = new AiCopilotMessagePO();
        msg.setSessionId(sessionId);
        msg.setTenantId(tenantId);
        msg.setRole(role);
        msg.setContentSummary(summary);
        msg.setTokenEstimate(estimateTokens(summary));
        messageMapper.insert(msg);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return 0L;
        }
        try {
            return SecurityUtils.getUserId(auth);
        } catch (Exception e) {
            return 0L;
        }
    }

    private String maskIfNeeded(String text) {
        if (!aiProperties.isPiiMask() || !StringUtils.hasText(text)) {
            return text;
        }
        String masked = PHONE_PATTERN.matcher(text).replaceAll("138****0000");
        masked = ID_CARD_PATTERN.matcher(masked).replaceAll("110***********0000");
        return masked;
    }

    static String truncate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= SUMMARY_MAX_LEN ? text : text.substring(0, SUMMARY_MAX_LEN);
    }

    static int estimateTokens(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return Math.max(1, text.length() / 4);
    }

    ParsedChainProposal parseChainProposal(String llmReply) {
        if (!StringUtils.hasText(llmReply)) {
            throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
        }
        String json = stripMarkdownJson(llmReply);
        try {
            JsonNode root = MAPPER.readTree(json);
            String chainData;
            String summary;
            if (root.has("chainData")) {
                JsonNode cd = root.get("chainData");
                chainData = cd.isTextual() ? cd.asText() : cd.toString();
                summary = root.has("summary") ? root.get("summary").asText() : "";
            } else {
                chainData = root.toString();
                summary = "";
            }
            return new ParsedChainProposal(chainData, summary);
        } catch (Exception e) {
            return new ParsedChainProposal(json, "");
        }
    }

    ParsedExpression parseExpression(String llmReply) {
        String json = stripMarkdownJson(llmReply);
        try {
            JsonNode root = MAPPER.readTree(json);
            String expression = root.has("expression") ? root.get("expression").asText() : llmReply;
            String explanation = root.has("explanation") ? root.get("explanation").asText() : "";
            return new ParsedExpression(expression, explanation);
        } catch (Exception e) {
            return new ParsedExpression(llmReply, "");
        }
    }

    ParsedDiagnosis parseDiagnosis(String llmReply) {
        if (!StringUtils.hasText(llmReply)) {
            throw new BizException(ErrorCode.AI_LLM_EMPTY_RESPONSE);
        }
        String json = stripMarkdownJson(llmReply);
        try {
            JsonNode root = MAPPER.readTree(json);
            String diagnosis = root.has("diagnosis") ? root.get("diagnosis").asText() : llmReply;
            String suggestion = root.has("suggestion") ? root.get("suggestion").asText() : "";
            return new ParsedDiagnosis(diagnosis, suggestion);
        } catch (Exception e) {
            return new ParsedDiagnosis(llmReply, "");
        }
    }

    private String resolveExecutionId(AiDiagnoseRequest request) {
        if (StringUtils.hasText(request.getExecutionId())) {
            return request.getExecutionId().trim();
        }
        if (StringUtils.hasText(request.getTraceId())) {
            return request.getTraceId().trim();
        }
        return null;
    }

    private ExecutionTrace fetchTrace(String executionId, String appCode) {
        if (!StringUtils.hasText(executionId)) {
            return null;
        }
        return collectorQueryAggregator.getExecutionTrace(executionId, appCode);
    }

    private String buildErrorSummary(AiDiagnoseRequest request, ExecutionTrace trace) {
        if (StringUtils.hasText(request.getErrorSummary())) {
            return request.getErrorSummary().trim();
        }
        if (trace != null && StringUtils.hasText(trace.getErrorMessage())) {
            return trace.getErrorMessage();
        }
        if (trace != null && trace.getEvents() != null) {
            return trace.getEvents().stream()
                    .filter(e -> StringUtils.hasText(e.getErrorMessage()))
                    .map(ChainEvent::getErrorMessage)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("执行失败，无详细错误消息");
        }
        return "执行失败，无详细错误消息";
    }

    private String summarizeTrace(ExecutionTrace trace) {
        if (trace == null) {
            return "（未找到执行轨迹，请确认 executionId 与采集器在线）";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("executionId=").append(nullToEmpty(trace.getExecutionId())).append('\n');
        sb.append("chainCode=").append(nullToEmpty(trace.getChainCode())).append('\n');
        sb.append("chainName=").append(nullToEmpty(trace.getChainName())).append('\n');
        sb.append("status=").append(trace.getStatus()).append('\n');
        sb.append("nodeCount=").append(trace.getNodeCount())
                .append(" success=").append(trace.getSuccessCount())
                .append(" failed=").append(trace.getFailedCount()).append('\n');
        if (StringUtils.hasText(trace.getErrorMessage())) {
            sb.append("errorMessage=").append(trace.getErrorMessage()).append('\n');
        }
        if (trace.getEvents() != null && !trace.getEvents().isEmpty()) {
            sb.append("events:\n");
            int limit = 60;
            int count = 0;
            for (ChainEvent event : trace.getEvents()) {
                if (count++ >= limit) {
                    sb.append("... (truncated)\n");
                    break;
                }
                sb.append("- ").append(event.getEventType());
                if (StringUtils.hasText(event.getNodeName())) {
                    sb.append(" node=").append(event.getNodeName());
                } else if (StringUtils.hasText(event.getNodeId())) {
                    sb.append(" nodeId=").append(event.getNodeId());
                }
                if (event.getCostMs() != null) {
                    sb.append(" costMs=").append(event.getCostMs());
                }
                if (StringUtils.hasText(event.getErrorMessage())) {
                    sb.append(" error=").append(event.getErrorMessage());
                }
                sb.append('\n');
            }
        }
        return maskIfNeeded(sb.toString());
    }

    private String buildOpenDesignPath(AiDiagnoseRequest request) {
        if (StringUtils.hasText(request.getDesignId()) && StringUtils.hasText(request.getAppCode())) {
            return "/design/" + request.getDesignId().trim() + "?appCode=" + request.getAppCode().trim();
        }
        if (StringUtils.hasText(request.getAppCode())) {
            return "/design?appCode=" + request.getAppCode().trim();
        }
        return null;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String stripMarkdownJson(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                return trimmed.substring(start + 1, end).trim();
            }
        }
        return trimmed;
    }

    private static String annotationLabel(com.zestflow.common.model.ComponentType type) {
        return switch (type) {
            case PREDICATE -> "@ZestPredicate";
            case SELECTOR -> "@ZestSelector";
            case LOADER -> "@ZestLoader";
            case PARSER -> "@ZestParser";
            default -> "@ZestExecute";
        };
    }

    record ParsedChainProposal(String chainData, String summary) {}
    record ParsedExpression(String expression, String explanation) {}
    record ParsedDiagnosis(String diagnosis, String suggestion) {}
}
