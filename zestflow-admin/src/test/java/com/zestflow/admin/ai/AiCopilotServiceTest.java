package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.dto.AiDiagnoseRequest;
import com.zestflow.admin.ai.model.dto.AiExplainRequest;
import com.zestflow.admin.ai.model.dto.AiSessionFeedbackDTO;
import com.zestflow.admin.ai.model.dto.AiSuggestRequest;
import com.zestflow.admin.ai.model.vo.AiDiagnoseResponse;
import com.zestflow.admin.ai.model.vo.AiExplainResponse;
import com.zestflow.admin.ai.model.vo.AiSuggestResponse;
import com.zestflow.admin.ai.model.vo.AiValidationVO;
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
    @Mock private AiCopilotPipeline copilotPipeline;
    @Mock private AiCopilotSessionSupport sessionSupport;
    @Mock private AiCopilotTraceService traceService;

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
        lenient().when(copilotPipeline.buildChatMessages(any(), anyLong(), anyString(), anyString(), anyString(), any()))
                .thenAnswer(inv -> List.of(
                        new AiChatClient.ChatMessage("system", "sys"),
                        new AiChatClient.ChatMessage("user", inv.getArgument(3))));

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
                executorChainAiClient,
                copilotPipeline,
                sessionSupport,
                traceService
        );
    }

    @Test
    void explain_delegatesToPipeline() {
        AiExplainRequest request = new AiExplainRequest();
        request.setAppCode("demo");
        AiExplainResponse expected = AiExplainResponse.builder()
                .explanation("ok")
                .sessionId(1L)
                .model("deepseek-chat")
                .build();
        when(copilotPipeline.explain(eq(request), any())).thenReturn(expected);

        AiExplainResponse response = service.explain(request);

        assertThat(response.getExplanation()).isEqualTo("ok");
        verify(copilotPipeline).explain(eq(request), any());
    }

    @Test
    void suggest_delegatesToPipeline() {
        AiSuggestRequest request = new AiSuggestRequest();
        request.setAppCode("demo");
        request.setUserMessage("创建链");
        AiSuggestResponse expected = AiSuggestResponse.builder()
                .summary("done")
                .proposedChainData("{}")
                .validation(AiValidationVO.builder().valid(true).errors(List.of()).build())
                .sessionId(2L)
                .build();
        when(copilotPipeline.suggest(eq(request), any())).thenReturn(expected);

        AiSuggestResponse response = service.suggest(request);

        assertThat(response.getSummary()).isEqualTo("done");
        verify(copilotPipeline).suggest(eq(request), any());
        verify(executorChainAiClient, never()).recordLearningEvent(anyString(), any());
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
    void parseChainProposal_shouldParseReasoning() {
        AiCopilotSessionSupport realSupport = new AiCopilotSessionSupport(
                aiPlatformConfig, sessionMapper, messageMapper);
        String raw = "{\"reasoning\":\"先校验再改状态\",\"chainData\":{\"nodes\":[]},\"summary\":\"ok\"}";
        AiCopilotService.ParsedChainProposal proposal = realSupport.parseChainProposal(raw);
        assertThat(proposal.reasoning()).isEqualTo("先校验再改状态");
        assertThat(proposal.summary()).isEqualTo("ok");
    }

    @Test
    void parseAssistantRecord_shouldSplitReasoningAndBody() {
        String raw = AiCopilotService.formatAssistantRecord("思考步骤", "结论摘要");
        AiCopilotService.ParsedAssistantContent parsed = AiCopilotService.parseAssistantRecord(raw);
        assertThat(parsed.reasoning()).isEqualTo("思考步骤");
        assertThat(parsed.body()).isEqualTo("结论摘要");
    }

    @Test
    void diagnose_shouldUseTraceAndLlm() {
        when(tenantAiConfigService.getCurrentTenantId()).thenReturn(1L);
        when(tenantAiConfigService.isCopilotEnabledForTenant(1L)).thenReturn(true);
        when(tenantAiConfigService.resolveEffectiveConfig(1L)).thenReturn(effectiveConfig());
        when(sessionSupport.recordSession(anyLong(), any(), any(), any(), any())).thenReturn(50L);
        when(sessionSupport.maskIfNeeded(any())).thenAnswer(inv -> inv.getArgument(0));

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
                "{\"diagnosis\":\"deductStock 节点库存校验失败\",\"suggestion\":\"修复\"}");

        AiDiagnoseRequest request = new AiDiagnoseRequest();
        request.setAppCode("demo-app");
        request.setExecutionId("exec-1");

        AiDiagnoseResponse response = service.diagnose(request);

        assertThat(response.isStub()).isFalse();
        assertThat(response.getDiagnosis()).contains("deductStock");
    }

    private static TenantAiConfigService.EffectiveAiConfig effectiveConfig() {
        return new TenantAiConfigService.EffectiveAiConfig(
                "deepseek", "https://api.deepseek.com", "deepseek-chat", "sk-x", true, true);
    }
}
