package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.dto.AiDiagnoseRequest;
import com.zestflow.admin.ai.model.dto.AiExplainRequest;
import com.zestflow.admin.ai.model.dto.AiSessionFeedbackDTO;
import com.zestflow.admin.ai.model.dto.AiSuggestRequest;
import com.zestflow.admin.ai.model.vo.AiDiagnoseResponse;
import com.zestflow.admin.ai.model.vo.AiExplainResponse;
import com.zestflow.admin.ai.model.vo.AiSuggestResponse;
import com.zestflow.admin.ai.model.vo.AiValidationVO;
import com.zestflow.admin.ai.model.entity.AiCopilotMessagePO;
import com.zestflow.admin.ai.model.entity.AiCopilotSessionPO;
import com.zestflow.admin.ai.repository.AiCopilotMessageMapper;
import com.zestflow.admin.ai.repository.AiCopilotSessionMapper;
import com.zestflow.admin.client.CollectorQueryAggregator;
import com.zestflow.admin.config.AiPlatformConfig;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.dto.ChainEvent;
import com.zestflow.common.protocol.ExecutionTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiCopilotServiceTest {

    @Mock private TenantAiConfigService tenantAiConfigService;
    @Mock private AiChatClient aiChatClient;
    @Mock private ExecutorValidateClient executorValidateClient;
    @Mock private CollectorQueryAggregator collectorQueryAggregator;
    @Mock private AiRagService aiRagService;
    @Mock private AiCopilotSessionMapper sessionMapper;
    @Mock private AiCopilotMessageMapper messageMapper;
    @Mock private AiQuotaService aiQuotaService;
    @Mock private AiLearningEventService aiLearningEventService;
    @Mock private ExecutorChainAiClient executorChainAiClient;

    private AiPlatformConfig aiPlatformConfig;
    private AiCopilotService service;

    @BeforeEach
    void setUp() {
        AiProperties yaml = new AiProperties();
        yaml.setEnabled(true);
        yaml.setRepairMaxRounds(2);
        aiPlatformConfig = AiPlatformConfigTestFixtures.fromYaml(yaml);

        lenient().when(aiRagService.retrieve(anyLong(), any(), anyString(), anyInt())).thenReturn(List.of());
        lenient().when(executorChainAiClient.searchRag(anyString(), anyString(), anyInt())).thenReturn(List.of());

        service = new AiCopilotService(
                aiPlatformConfig,
                tenantAiConfigService,
                aiChatClient,
                new PromptBuilder(),
                executorValidateClient,
                collectorQueryAggregator,
                aiRagService,
                sessionMapper,
                messageMapper,
                aiQuotaService,
                aiLearningEventService,
                executorChainAiClient
        );

        lenient().when(tenantAiConfigService.getCurrentTenantId()).thenReturn(1L);
        lenient().doAnswer(inv -> {
            AiCopilotSessionPO session = inv.getArgument(0);
            session.setId(100L);
            return 1;
        }).when(sessionMapper).insert(any(AiCopilotSessionPO.class));
    }

    @Test
    void explain_whenCopilotDisabled_shouldThrow() {
        when(tenantAiConfigService.isCopilotEnabledForTenant(1L)).thenReturn(false);

        AiExplainRequest request = new AiExplainRequest();
        request.setAppCode("demo");

        assertThatThrownBy(() -> service.explain(request))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_COPILOT_DISABLED);
    }

    @Test
    void explain_shouldReturnExplanation() {
        when(tenantAiConfigService.isCopilotEnabledForTenant(1L)).thenReturn(true);
        when(tenantAiConfigService.resolveEffectiveConfig(1L)).thenReturn(effectiveConfig());
        when(aiChatClient.chat(anyList(), any())).thenReturn("这是一条线性链，依次校验、扣库存、支付。");

        AiExplainRequest request = new AiExplainRequest();
        request.setAppCode("demo");
        request.setCurrentChainData("{\"nodes\":[]}");
        request.setAllowedComponents(List.of("payOrder"));

        AiExplainResponse response = service.explain(request);

        assertThat(response.getExplanation()).contains("线性链");
        assertThat(response.getSessionId()).isEqualTo(100L);
        verify(sessionMapper).insert(any(AiCopilotSessionPO.class));
        verify(messageMapper, times(2)).insert(any(AiCopilotMessagePO.class));
    }

    @Test
    void suggest_shouldRepairUntilValid() {
        when(tenantAiConfigService.isCopilotEnabledForTenant(1L)).thenReturn(true);
        when(tenantAiConfigService.resolveEffectiveConfig(1L)).thenReturn(effectiveConfig());

        String invalidJson = "{\"chainData\":{\"nodes\":["
                + "{\"id\":\"n1\",\"type\":\"NORMAL\"},{\"id\":\"n2\",\"type\":\"NORMAL\"},"
                + "{\"id\":\"n3\",\"type\":\"CONDITION\"},{\"id\":\"n4\",\"type\":\"NORMAL\"}"
                + "]},\"summary\":\"初稿\"}";
        String fixedJson = "{\"chainData\":{\"nodes\":[{\"id\":\"n1\"}]},\"summary\":\"修复后\"}";

        when(aiChatClient.chat(anyList(), any()))
                .thenReturn(invalidJson)
                .thenReturn(fixedJson);

        when(executorValidateClient.validate(eq("demo"), anyString()))
                .thenReturn(AiValidationVO.builder().valid(false).errors(List.of("缺少 START 节点")).build())
                .thenReturn(AiValidationVO.builder().valid(true).errors(List.of()).build());

        AiSuggestRequest request = new AiSuggestRequest();
        request.setAppCode("demo");
        request.setUserMessage("创建下单链");
        request.setAllowedComponents(List.of("payOrder"));

        AiSuggestResponse response = service.suggest(request);

        assertThat(response.getValidation().isValid()).isTrue();
        assertThat(response.getRepairRounds()).isEqualTo(1);
        assertThat(response.getSummary()).isEqualTo("修复后");
        verify(aiChatClient, times(2)).chat(anyList(), any());
    }

    @Test
    void recordFeedback_playgroundSuccessOnly_forwardsToExecutor() {
        AiCopilotSessionPO session = new AiCopilotSessionPO();
        session.setId(9L);
        session.setTenantId(1L);
        session.setAppCode("demo");
        session.setMode("COMPOSE_CHAIN");
        session.setChainCode("userRegister");
        when(sessionMapper.selectById(9L)).thenReturn(session);
        when(tenantAiConfigService.getCurrentTenantId()).thenReturn(1L);

        AiSessionFeedbackDTO dto = new AiSessionFeedbackDTO();
        dto.setPlaygroundSuccess(true);
        dto.setIntent("COMPOSE_CHAIN");
        dto.setFeature("userRegister");
        dto.setChainData("{\"nodes\":[]}");

        service.recordFeedback(9L, dto);

        verify(executorChainAiClient).recordLearningEvent(eq("demo"), argThat(event ->
                Boolean.TRUE.equals(event.get("playgroundSuccess"))
                        && "COMPOSE_CHAIN".equals(event.get("intent"))));
        verify(sessionMapper).updateById(any(AiCopilotSessionPO.class));
    }

    @Test
    void parseChainProposal_shouldStripMarkdownFence() {
        String raw = "```json\n{\"chainData\":{\"nodes\":[]},\"summary\":\"ok\"}\n```";
        AiCopilotService.ParsedChainProposal proposal = service.parseChainProposal(raw);
        assertThat(proposal.summary()).isEqualTo("ok");
        assertThat(proposal.chainData()).contains("nodes");
    }

    @Test
    void diagnose_shouldUseTraceAndLlm() {
        when(tenantAiConfigService.isCopilotEnabledForTenant(1L)).thenReturn(true);
        when(tenantAiConfigService.resolveEffectiveConfig(1L)).thenReturn(effectiveConfig());

        ExecutionTrace trace = ExecutionTrace.builder()
                .executionId("exec-1")
                .chainCode("ORDER_PAY")
                .errorMessage("库存不足")
                .events(List.of(ChainEvent.builder()
                        .eventType(ChainEvent.EventType.NODE_FAILED)
                        .nodeName("deductStock")
                        .errorMessage("库存不足")
                        .build()))
                .build();
        when(collectorQueryAggregator.getExecutionTrace("exec-1", "demo-app")).thenReturn(trace);

        when(aiChatClient.chat(anyList(), any())).thenReturn(
                "{\"diagnosis\":\"deductStock 节点库存校验失败\",\"suggestion\":\"检查入参 sku 与库存数量\"}");

        AiDiagnoseRequest request = new AiDiagnoseRequest();
        request.setAppCode("demo-app");
        request.setExecutionId("exec-1");
        request.setChainCode("ORDER_PAY");

        AiDiagnoseResponse response = service.diagnose(request);

        assertThat(response.isStub()).isFalse();
        assertThat(response.getDiagnosis()).contains("deductStock");
        assertThat(response.getSuggestion()).contains("库存");
        assertThat(response.getOpenDesignPath()).contains("/design");
    }

    @Test
    void parseDiagnosis_shouldParseJson() {
        AiCopilotService.ParsedDiagnosis parsed = service.parseDiagnosis(
                "{\"diagnosis\":\"根因\",\"suggestion\":\"建议\"}");
        assertThat(parsed.diagnosis()).isEqualTo("根因");
        assertThat(parsed.suggestion()).isEqualTo("建议");
    }

    private static TenantAiConfigService.EffectiveAiConfig effectiveConfig() {
        return new TenantAiConfigService.EffectiveAiConfig(
                "deepseek", "https://api.deepseek.com", "deepseek-chat", "sk-x", true, true);
    }
}
