package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.TenantAiConfigService.EffectiveAiConfig;
import com.zestflow.admin.config.AiPlatformConfig;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Copilot 核心业务服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiCopilotService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int SUMMARY_MAX_LEN = 2000;
    private static final String ASSISTANT_REASONING_PREFIX = "【思考】\n";
    private static final String ASSISTANT_BODY_PREFIX = "\n\n【回复】\n";

    private final AiPlatformConfig aiPlatformConfig;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiChatClient aiChatClient;
    private final PromptBuilder promptBuilder;
    private final ExecutorValidateClient executorValidateClient;
    private final CollectorQueryAggregator collectorQueryAggregator;
    private final AiRagService aiRagService;
    private final AiCopilotSessionMapper sessionMapper;
    private final AiCopilotMessageMapper messageMapper;
    private final AiQuotaService aiQuotaService;
    private final AiLearningEventService aiLearningEventService;
    private final ExecutorChainAiClient executorChainAiClient;
    private final AiCopilotPipeline copilotPipeline;
    private final AiCopilotSessionSupport sessionSupport;
    private final AiCopilotTraceService traceService;

    public AiExplainResponse explain(AiExplainRequest request) {
        return copilotPipeline.explain(request, AiCopilotStreamSink.noop());
    }

    public AiSuggestResponse suggest(AiSuggestRequest request) {
        return copilotPipeline.suggest(request, AiCopilotStreamSink.noop());
    }

    public void explainStream(AiExplainRequest request, AiCopilotStreamSink sink) {
        copilotPipeline.explain(request, sink);
    }

    public void suggestStream(AiSuggestRequest request, AiCopilotStreamSink sink) {
        copilotPipeline.suggest(request, sink);
    }

    public AiCopilotSessionDetailVO loadActiveSession(String appCode, String designId, String chainCode) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        Long userId = currentUserId();
        AiCopilotSessionPO session = sessionSupport.findLatestSession(tenantId, userId, appCode, designId, chainCode);
        if (session == null) {
            return null;
        }
        return buildSessionDetail(session, tenantId);
    }

    public List<AiCopilotSessionSummaryVO> listSessions(String appCode, String designId, String chainCode, int limit) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        Long userId = currentUserId();
        List<AiCopilotSessionPO> sessions = sessionSupport.listSessions(
                tenantId, userId, appCode, designId, chainCode, limit);
        return sessions.stream().map(s -> toSessionSummary(s, tenantId)).toList();
    }

    public AiCopilotSessionDetailVO getSessionDetail(Long sessionId) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        Long userId = currentUserId();
        AiCopilotSessionPO session = sessionSupport.requireSession(sessionId, tenantId, userId);
        return buildSessionDetail(session, tenantId);
    }

    public AiCopilotSessionDetailVO createSession(AiCopilotSessionCreateDTO dto) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        Long userId = currentUserId();
        Long sessionId = sessionSupport.createSession(tenantId, userId, dto.getAppCode(),
                dto.getDesignId(), dto.getChainCode(), dto.getMode(), dto.getTitle());
        AiCopilotSessionPO session = sessionSupport.requireSession(sessionId, tenantId, userId);
        return buildSessionDetail(session, tenantId);
    }

    public AiCopilotSessionDetailVO updateSession(Long sessionId, AiCopilotSessionUpdateDTO dto) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        Long userId = currentUserId();
        sessionSupport.requireSession(sessionId, tenantId, userId);
        if (StringUtils.hasText(dto.getTitle())) {
            sessionSupport.updateTitle(sessionId, dto.getTitle());
        }
        AiCopilotSessionPO session = sessionSupport.requireSession(sessionId, tenantId, userId);
        return buildSessionDetail(session, tenantId);
    }

    public void archiveSession(Long sessionId) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        Long userId = currentUserId();
        sessionSupport.requireSession(sessionId, tenantId, userId);
        sessionSupport.archiveSession(sessionId);
    }

    public List<AiCopilotTraceStepVO> listSessionTrace(Long sessionId) {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        Long userId = currentUserId();
        sessionSupport.requireSession(sessionId, tenantId, userId);
        return traceService.listBySession(sessionId, tenantId);
    }

    private AiCopilotSessionDetailVO buildSessionDetail(AiCopilotSessionPO session, Long tenantId) {
        String model = StringUtils.hasText(session.getLastModel())
                ? session.getLastModel()
                : tenantAiConfigService.resolveEffectiveConfig(tenantId).model();
        return AiCopilotSessionDetailVO.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .mode(session.getMode())
                .model(model)
                .messages(listSessionMessages(session.getId(), tenantId))
                .pendingChainData(session.getPendingChainData())
                .pendingSummary(session.getPendingSummary())
                .pendingValidation(sessionSupport.readPendingValidation(session))
                .build();
    }

    private AiCopilotSessionSummaryVO toSessionSummary(AiCopilotSessionPO session, Long tenantId) {
        long messageCount = messageMapper.selectCount(new LambdaQueryWrapper<AiCopilotMessagePO>()
                .eq(AiCopilotMessagePO::getSessionId, session.getId())
                .eq(AiCopilotMessagePO::getTenantId, tenantId));
        AiCopilotMessagePO lastMsg = messageMapper.selectOne(new LambdaQueryWrapper<AiCopilotMessagePO>()
                .eq(AiCopilotMessagePO::getSessionId, session.getId())
                .eq(AiCopilotMessagePO::getTenantId, tenantId)
                .orderByDesc(AiCopilotMessagePO::getCreatedAt)
                .orderByDesc(AiCopilotMessagePO::getId)
                .last("LIMIT 1"));
        String preview = lastMsg != null ? sessionSupport.truncate(lastMsg.getContentSummary()) : null;
        return AiCopilotSessionSummaryVO.builder()
                .sessionId(session.getId())
                .title(session.getTitle())
                .mode(session.getMode())
                .lastModel(session.getLastModel())
                .success(session.getSuccess() == null || session.getSuccess() == 1)
                .latencyMs(session.getLatencyMs())
                .messageCount((int) messageCount)
                .hasPending(StringUtils.hasText(session.getPendingChainData()))
                .lastMessagePreview(preview)
                .createdAt(session.getCreatedAt())
                .build();
    }

    public List<AiCopilotMessageVO> listSessionMessages(Long sessionId, Long tenantId) {
        AiCopilotSessionPO session = sessionMapper.selectById(sessionId);
        if (session == null || !tenantId.equals(session.getTenantId())) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        List<AiCopilotMessagePO> rows = messageMapper.selectList(new LambdaQueryWrapper<AiCopilotMessagePO>()
                .eq(AiCopilotMessagePO::getSessionId, sessionId)
                .eq(AiCopilotMessagePO::getTenantId, tenantId)
                .orderByAsc(AiCopilotMessagePO::getCreatedAt)
                .orderByAsc(AiCopilotMessagePO::getId));
        return rows.stream().map(sessionSupport::toMessageVo).toList();
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

        Long sessionId = sessionSupport.recordSession(tenantId, request.getAppCode(), request.getDesignId(),
                request.getChainCode(), "expression");
        long startMs = System.currentTimeMillis();
        try {
            String reply = invokeSimpleChat(config, system, user, true, tenantId, request.getAppCode(), sessionId);
            ParsedExpression parsed = parseExpression(reply);
            sessionSupport.recordMessage(sessionId, tenantId, "user", sessionSupport.truncate(request.getUserMessage()));
            sessionSupport.recordMessage(sessionId, tenantId, "assistant", sessionSupport.truncate(parsed.expression()));
            sessionSupport.finalizeSession(sessionId, startMs, true, null, config.model());
            return AiExpressionSuggestResponse.builder()
                    .expression(parsed.expression())
                    .explanation(parsed.explanation())
                    .sessionId(sessionId)
                    .build();
        } catch (RuntimeException e) {
            sessionSupport.finalizeSession(sessionId, startMs, false, e.getMessage(), config.model());
            throw e;
        }
    }

    public AiDiagnoseResponse diagnose(AiDiagnoseRequest request) {
        requireCopilotEnabled();
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        EffectiveAiConfig config = tenantAiConfigService.resolveEffectiveConfig(tenantId);

        String executionId = resolveExecutionId(request);
        ExecutionTrace trace = fetchTrace(executionId, request.getAppCode());
        String errorSummary = buildErrorSummary(request, trace);
        String traceSummary = summarizeTrace(trace);

        Long sessionId = sessionSupport.recordSession(tenantId, request.getAppCode(), request.getDesignId(),
                request.getChainCode(), "diagnose");
        long startMs = System.currentTimeMillis();

        try {
            String system = promptBuilder.buildSystemPrompt("diagnose", null);
            String user = promptBuilder.buildDiagnoseUserPrompt(errorSummary, traceSummary);
            String reply = invokeSimpleChat(config, system, user, true, tenantId, request.getAppCode(), sessionId);
            ParsedDiagnosis parsed = parseDiagnosis(reply);
            sessionSupport.recordMessage(sessionId, tenantId, "user", sessionSupport.truncate(errorSummary));
            sessionSupport.recordMessage(sessionId, tenantId, "assistant", sessionSupport.truncate(parsed.diagnosis()));
            sessionSupport.finalizeSession(sessionId, startMs, true, null, config.model());

            return AiDiagnoseResponse.builder()
                    .diagnosis(parsed.diagnosis())
                    .suggestion(parsed.suggestion())
                    .stub(false)
                    .sessionId(sessionId)
                    .openDesignPath(buildOpenDesignPath(request))
                    .build();
        } catch (Exception e) {
            log.warn("AI 日志诊断失败 executionId={}", executionId, e);
            sessionSupport.finalizeSession(sessionId, startMs, false, e.getMessage(), config.model());
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

        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        EffectiveAiConfig config = tenantAiConfigService.resolveForTest(tenantId, override);
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
        if (dto.getAdopted() != null && dto.getAdopted() == 1) {
            sessionSupport.clearPendingProposal(sessionId);
        }
        // 蒸馏仅在人机闭环（采纳/试跑成功）时触发，不对裸生成自动晋升
        if (Boolean.TRUE.equals(dto.getPlaygroundSuccess())
                || (dto.getAdopted() != null && dto.getAdopted() == 1)) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("intent", dto.getIntent() != null ? dto.getIntent() : session.getMode());
            event.put("feature", dto.getFeature() != null ? dto.getFeature() : session.getChainCode());
            event.put("appCode", session.getAppCode());
            event.put("chainCode", session.getChainCode());
            event.put("httpMode", dto.getHttpMode());
            event.put("validatePassed", dto.getValidatePassed());
            event.put("validateRounds", dto.getValidateRounds());
            event.put("adopted", dto.getAdopted() != null && dto.getAdopted() == 1);
            event.put("playgroundSuccess", dto.getPlaygroundSuccess());
            event.put("userCorrection", dto.getUserCorrection());
            event.put("chainData", dto.getChainData());
            executorChainAiClient.recordLearningEvent(session.getAppCode(), event);

            AiLearningEventSaveDTO learningDto = new AiLearningEventSaveDTO();
            learningDto.setSessionId(sessionId);
            learningDto.setAppCode(session.getAppCode());
            learningDto.setIntent(dto.getIntent() != null ? dto.getIntent() : session.getMode());
            learningDto.setFeature(dto.getFeature() != null ? dto.getFeature() : session.getChainCode());
            learningDto.setChainCode(session.getChainCode());
            learningDto.setHttpMode(dto.getHttpMode());
            learningDto.setValidatePassed(dto.getValidatePassed());
            learningDto.setValidateRounds(dto.getValidateRounds());
            learningDto.setAdopted(dto.getAdopted() != null && dto.getAdopted() == 1);
            learningDto.setPlaygroundSuccess(dto.getPlaygroundSuccess());
            learningDto.setUserCorrection(dto.getUserCorrection());
            aiLearningEventService.record(learningDto);
        }
    }

    private void requireCopilotEnabled() {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        if (!tenantAiConfigService.isCopilotEnabledForTenant(tenantId)) {
            throw new BizException(ErrorCode.AI_COPILOT_DISABLED);
        }
        aiQuotaService.ensureWithinQuota(tenantId);
    }

    private String invokeSimpleChat(EffectiveAiConfig config, String system, String user, boolean jsonMode,
                                    Long tenantId, String appCode, Long sessionId) {
        List<AiChatClient.ChatMessage> messages = copilotPipeline.buildChatMessages(
                sessionId, tenantId, system, user, user, appCode);
        return aiChatClient.chat(messages, buildOptions(config, jsonMode));
    }

    private AiChatClient.AiChatOptions buildOptions(EffectiveAiConfig config) {
        return buildOptions(config, false);
    }

    private AiChatClient.AiChatOptions buildOptions(EffectiveAiConfig config, boolean jsonMode) {
        return new AiChatClient.AiChatOptions(
                config.baseUrl(),
                config.apiKey(),
                config.model(),
                aiPlatformConfig.getTimeoutMs(),
                aiPlatformConfig.getMaxTokens(),
                aiPlatformConfig.getTemperature(),
                jsonMode
        );
    }

    static String formatAssistantRecord(String reasoning, String body) {
        if (!StringUtils.hasText(reasoning)) {
            return body != null ? body : "";
        }
        return ASSISTANT_REASONING_PREFIX + reasoning.trim()
                + ASSISTANT_BODY_PREFIX + (body != null ? body : "");
    }

    static ParsedAssistantContent parseAssistantRecord(String content) {
        if (!StringUtils.hasText(content)) {
            return new ParsedAssistantContent(null, "");
        }
        if (content.startsWith(ASSISTANT_REASONING_PREFIX)) {
            int bodyIdx = content.indexOf(ASSISTANT_BODY_PREFIX);
            if (bodyIdx > 0) {
                String reasoning = content.substring(ASSISTANT_REASONING_PREFIX.length(), bodyIdx).trim();
                String body = content.substring(bodyIdx + ASSISTANT_BODY_PREFIX.length()).trim();
                return new ParsedAssistantContent(reasoning, body);
            }
        }
        return new ParsedAssistantContent(null, content);
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
        return sessionSupport.parseChainProposal(llmReply);
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

    private String maskIfNeeded(String text) {
        return sessionSupport.maskIfNeeded(text);
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

    record ParsedChainProposal(String chainData, String summary, String reasoning) {}
    record ParsedAssistantContent(String reasoning, String body) {}
    record ParsedExpression(String expression, String explanation) {}
    record ParsedDiagnosis(String diagnosis, String suggestion) {}
}
