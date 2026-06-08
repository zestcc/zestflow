package com.zestflow.admin.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.admin.ai.TenantAiConfigService.EffectiveAiConfig;
import com.zestflow.admin.ai.model.dto.AiExplainRequest;
import com.zestflow.admin.ai.model.dto.AiSuggestRequest;
import com.zestflow.admin.ai.model.entity.AiCopilotMessagePO;
import com.zestflow.admin.ai.model.entity.AiCopilotSessionPO;
import com.zestflow.admin.ai.model.vo.AiExplainResponse;
import com.zestflow.admin.ai.model.vo.AiSuggestResponse;
import com.zestflow.admin.ai.model.vo.AiValidationVO;
import com.zestflow.admin.ai.repository.AiCopilotMessageMapper;
import com.zestflow.admin.ai.repository.AiCopilotSessionMapper;
import com.zestflow.admin.config.AiPlatformConfig;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.util.SecurityUtils;
import com.zestflow.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Copilot 编排流水线：多轮上下文 + 流式 LLM + 质量门/校验修复（对标 ChatGPT/Cursor）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiCopilotPipeline {

    private final AiPlatformConfig aiPlatformConfig;
    private final TenantAiConfigService tenantAiConfigService;
    private final AiChatClient aiChatClient;
    private final PromptBuilder promptBuilder;
    private final ExecutorValidateClient executorValidateClient;
    private final AiRagService aiRagService;
    private final AiCopilotSessionMapper sessionMapper;
    private final AiCopilotMessageMapper messageMapper;
    private final AiQuotaService aiQuotaService;
    private final ExecutorChainAiClient executorChainAiClient;
    private final AiCopilotSessionSupport sessionSupport;
    private final AiCopilotTraceService traceService;

    public AiExplainResponse explain(AiExplainRequest request, AiCopilotStreamSink sink) {
        requireCopilotEnabled();
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        EffectiveAiConfig config = tenantAiConfigService.resolveEffectiveConfig(tenantId);

        String chainData = sessionSupport.maskIfNeeded(request.getCurrentChainData());
        String graphData = sessionSupport.maskIfNeeded(request.getGraphData());
        String system = promptBuilder.buildSystemPrompt("explain", request.getAllowedComponents());
        String userPrompt = StringUtils.hasText(request.getUserMessage())
                ? request.getUserMessage().trim() : "解释当前链";
        String user = promptBuilder.buildUserPrompt("explain", userPrompt, chainData, null, graphData);

        Long sessionId = sessionSupport.resolveOrCreateSession(tenantId, request.getAppCode(),
                request.getDesignId(), request.getChainCode(), "explain", request.getSessionId());
        sessionSupport.maybeSetTitleFromUserMessage(sessionId, userPrompt);
        sink.progress("检索知识库…");
        long startMs = System.currentTimeMillis();
        try {
            StringBuilder reasoningBuf = new StringBuilder();
            String reply = traceService.runStepWithResult(sessionId, "LLM", "生成解释",
                    () -> invokeLlm(config, system, user, false, tenantId, request.getAppCode(),
                            sessionId, sink, reasoningBuf));
            sessionSupport.recordMessage(sessionId, tenantId, "user", sessionSupport.truncate(userPrompt));
            sessionSupport.recordMessage(sessionId, tenantId, "assistant", sessionSupport.truncate(reply));
            sessionSupport.finalizeSession(sessionId, startMs, true, null, config.model());
            AiExplainResponse response = AiExplainResponse.builder()
                    .explanation(reply)
                    .sessionId(sessionId)
                    .model(config.model())
                    .build();
            sink.explainDone(response);
            return response;
        } catch (RuntimeException e) {
            sessionSupport.finalizeSession(sessionId, startMs, false, e.getMessage(), config.model());
            sink.error(e.getMessage());
            throw e;
        }
    }

    public AiSuggestResponse suggest(AiSuggestRequest request, AiCopilotStreamSink sink) {
        requireCopilotEnabled();
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        EffectiveAiConfig config = tenantAiConfigService.resolveEffectiveConfig(tenantId);

        String mode = StringUtils.hasText(request.getMode()) ? request.getMode() : "generate";
        String chainData = sessionSupport.maskIfNeeded(request.getCurrentChainData());
        String graphData = sessionSupport.maskIfNeeded(request.getGraphData());
        String system = promptBuilder.buildSystemPrompt(mode, request.getAllowedComponents());
        String user = promptBuilder.buildUserPrompt(mode, request.getUserMessage(), chainData, null, graphData);

        Long sessionId = sessionSupport.resolveOrCreateSession(tenantId, request.getAppCode(),
                request.getDesignId(), request.getChainCode(), "suggest", request.getSessionId());
        sessionSupport.maybeSetTitleFromUserMessage(sessionId, request.getUserMessage());

        List<String> progressSteps = new ArrayList<>();
        progressSteps.add("检索应用端与租户 RAG…");
        sink.progress(progressSteps.get(0));
        long ragStep = traceService.startStep(sessionId, "RAG", progressSteps.get(0));
        traceService.finishStep(ragStep, true, null, 0, null);
        long startMs = System.currentTimeMillis();
        try {
            progressSteps.add("调用大模型生成链草案…");
            sink.progress(progressSteps.get(1));
            StringBuilder reasoningBuf = new StringBuilder();
            String llmReply = traceService.runStepWithResult(sessionId, "LLM", progressSteps.get(1),
                    () -> invokeLlm(config, system, user, true, tenantId, request.getAppCode(),
                            sessionId, sink, reasoningBuf));
            AiCopilotService.ParsedChainProposal proposal = sessionSupport.parseChainProposal(llmReply);

            int qualityRetries = 0;
            while (qualityRetries < aiPlatformConfig.getRepairMaxRounds()) {
                AiChainQualityGate.QualityResult quality = AiChainQualityGate.assess(
                        request.getUserMessage(), proposal.chainData());
                if (quality.accepted()) {
                    break;
                }
                qualityRetries++;
                String step = "质量复检第 " + qualityRetries + " 轮…";
                progressSteps.add(step);
                sink.progress(step);
                String retryUser = promptBuilder.buildQualityRetryUserPrompt(
                        request.getUserMessage(), proposal.chainData(), quality.critique());
                llmReply = traceService.runStepWithResult(sessionId, "QUALITY", step,
                        () -> invokeLlm(config, system, retryUser, true, tenantId, request.getAppCode(),
                                sessionId, sink, reasoningBuf));
                proposal = sessionSupport.parseChainProposal(llmReply);
            }

            progressSteps.add("Executor 校验链定义…");
            sink.progress("Executor 校验链定义…");
            AiValidationVO validation = traceService.runStepWithResult(sessionId, "VALIDATE", progressSteps.get(progressSteps.size() - 1),
                    () -> executorValidateClient.validate(request.getAppCode(), proposal.chainData()));
            int repairRounds = 0;

            while (!validation.isValid() && repairRounds < aiPlatformConfig.getRepairMaxRounds()) {
                repairRounds++;
                String step = "自动修复第 " + repairRounds + " 轮…";
                progressSteps.add(step);
                sink.progress(step);
                String fixSystem = promptBuilder.buildSystemPrompt("fix-errors", request.getAllowedComponents());
                String fixUser = promptBuilder.buildUserPrompt("fix-errors", request.getUserMessage(),
                        proposal.chainData(), validation.getErrors(), graphData);
                llmReply = traceService.runStepWithResult(sessionId, "REPAIR", step,
                        () -> invokeLlm(config, fixSystem, fixUser, true, tenantId, request.getAppCode(),
                                sessionId, sink, reasoningBuf));
                proposal = sessionSupport.parseChainProposal(llmReply);
                validation = traceService.runStepWithResult(sessionId, "VALIDATE", "修复后校验",
                        () -> executorValidateClient.validate(request.getAppCode(), proposal.chainData()));
            }

            String resultStep = validation.isValid() ? "校验通过" : "校验未通过";
            progressSteps.add(resultStep);
            sink.progress(resultStep);
            long doneStep = traceService.startStep(sessionId, "DONE", resultStep);
            traceService.finishStep(doneStep, validation.isValid(), null, 0, null);

            sessionSupport.recordMessage(sessionId, tenantId, "user",
                    sessionSupport.truncate(request.getUserMessage()));
            String assistantRecord = AiCopilotService.formatAssistantRecord(
                    proposal.reasoning(), proposal.summary());
            sessionSupport.recordMessage(sessionId, tenantId, "assistant",
                    sessionSupport.truncate(assistantRecord));
            sessionSupport.savePendingProposal(sessionId, proposal.chainData(), proposal.summary(), validation);
            sessionSupport.finalizeSession(sessionId, startMs, true, null, config.model());

            AiSuggestResponse response = AiSuggestResponse.builder()
                    .proposedChainData(proposal.chainData())
                    .summary(proposal.summary())
                    .reasoning(proposal.reasoning())
                    .validation(validation)
                    .sessionId(sessionId)
                    .repairRounds(repairRounds)
                    .model(config.model())
                    .progressSteps(progressSteps)
                    .build();
            sink.suggestDone(response);
            return response;
        } catch (RuntimeException e) {
            sessionSupport.finalizeSession(sessionId, startMs, false, e.getMessage(), config.model());
            sink.error(e.getMessage());
            throw e;
        }
    }

    private String invokeLlm(EffectiveAiConfig config, String system, String user, boolean jsonMode,
                             Long tenantId, String appCode, Long sessionId,
                             AiCopilotStreamSink sink, StringBuilder reasoningAccumulator) {
        List<AiChatClient.ChatMessage> messages = buildChatMessages(
                sessionId, tenantId, system, user, user, appCode);
        AiChatClient.AiChatOptions options = new AiChatClient.AiChatOptions(
                config.baseUrl(), config.apiKey(), config.model(),
                aiPlatformConfig.getTimeoutMs(), aiPlatformConfig.getMaxTokens(),
                aiPlatformConfig.getTemperature(), jsonMode, true);
        AiChatClient.StreamHandlers handlers = AiChatClient.StreamHandlers.of(
                delta -> {
                    if (reasoningAccumulator != null) {
                        reasoningAccumulator.append(delta);
                    }
                    sink.reasoningDelta(delta);
                },
                sink::contentDelta);
        return aiChatClient.chatStream(messages, options, handlers);
    }

    List<AiChatClient.ChatMessage> buildChatMessages(Long sessionId, Long tenantId, String system,
                                                     String currentUser, String ragQuery, String appCode) {
        List<AiChatClient.ChatMessage> messages = new ArrayList<>();
        messages.add(new AiChatClient.ChatMessage("system", enrichSystemWithRag(system, ragQuery, tenantId, appCode)));
        if (sessionId != null) {
            List<AiCopilotMessagePO> history = messageMapper.selectList(
                    new LambdaQueryWrapper<AiCopilotMessagePO>()
                            .eq(AiCopilotMessagePO::getSessionId, sessionId)
                            .eq(AiCopilotMessagePO::getTenantId, tenantId)
                            .orderByAsc(AiCopilotMessagePO::getCreatedAt)
                            .orderByAsc(AiCopilotMessagePO::getId));
            List<AiCopilotMessagePO> trimmed = trimHistory(history);
            for (AiCopilotMessagePO row : trimmed) {
                if (!"user".equals(row.getRole()) && !"assistant".equals(row.getRole())) {
                    continue;
                }
                String text = toLlmHistoryText(row);
                if (StringUtils.hasText(text)) {
                    messages.add(new AiChatClient.ChatMessage(row.getRole(), text));
                }
            }
        }
        messages.add(new AiChatClient.ChatMessage("user", currentUser));
        return messages;
    }

    private List<AiCopilotMessagePO> trimHistory(List<AiCopilotMessagePO> history) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        int maxMsgs = Math.max(2, aiPlatformConfig.getContextMaxMessages());
        int maxChars = Math.max(2000, aiPlatformConfig.getContextMaxChars());
        List<AiCopilotMessagePO> reversed = new ArrayList<>(history);
        Collections.reverse(reversed);
        List<AiCopilotMessagePO> picked = new ArrayList<>();
        int chars = 0;
        for (AiCopilotMessagePO row : reversed) {
            if (picked.size() >= maxMsgs) {
                break;
            }
            String text = toLlmHistoryText(row);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            if (chars + text.length() > maxChars && !picked.isEmpty()) {
                break;
            }
            picked.add(row);
            chars += text.length();
        }
        Collections.reverse(picked);
        return picked;
    }

    private static String toLlmHistoryText(AiCopilotMessagePO row) {
        if ("assistant".equals(row.getRole())) {
            AiCopilotService.ParsedAssistantContent parsed =
                    AiCopilotService.parseAssistantRecord(row.getContentSummary());
            if (StringUtils.hasText(parsed.reasoning()) && StringUtils.hasText(parsed.body())) {
                return "[思考] " + parsed.reasoning() + "\n[回复] " + parsed.body();
            }
            return StringUtils.hasText(parsed.body()) ? parsed.body() : row.getContentSummary();
        }
        return row.getContentSummary();
    }

    private String enrichSystemWithRag(String system, String userQuery, Long tenantId, String appCode) {
        StringBuilder sb = new StringBuilder(system);
        List<String> executorSnippets = executorChainAiClient.searchRag(
                appCode, userQuery, aiPlatformConfig.getRagMaxChunks());
        if (!executorSnippets.isEmpty()) {
            sb.append("\n\n【应用端 RAG — 优先遵循】\n");
            for (String snippet : executorSnippets) {
                sb.append("---\n").append(snippet).append('\n');
            }
        }
        if (aiPlatformConfig.isRagEnabled()) {
            List<String> tenantSnippets = aiRagService.retrieve(
                    tenantId, appCode, userQuery, Math.max(1, aiPlatformConfig.getRagMaxChunks() / 2));
            if (!tenantSnippets.isEmpty()) {
                sb.append("\n\n【租户 RAG 补充】\n");
                for (String snippet : tenantSnippets) {
                    sb.append("---\n").append(snippet).append('\n');
                }
            }
        }
        sb.append("\n\n【多轮对话】请结合上文用户与助手历史继续推理，保持 chainData 与上下文一致。");
        return sb.toString();
    }

    private void requireCopilotEnabled() {
        Long tenantId = tenantAiConfigService.getCurrentTenantId();
        if (!tenantAiConfigService.isCopilotEnabledForTenant(tenantId)) {
            throw new BizException(ErrorCode.AI_COPILOT_DISABLED);
        }
        aiQuotaService.ensureWithinQuota(tenantId);
    }
}
