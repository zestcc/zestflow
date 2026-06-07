package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.dto.AiLearningEventSaveDTO;
import com.zestflow.admin.ai.model.entity.AiLearningEventPO;
import com.zestflow.admin.ai.repository.AiLearningEventMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiLearningEventServiceTest {

    @Mock private AiLearningEventMapper learningEventMapper;
    @Mock private TenantAiConfigService tenantAiConfigService;
    @Mock private AiRagDocumentService ragDocumentService;

    @InjectMocks
    private AiLearningEventService service;

    @Test
    void record_highQualityEvent_isPromotionEligible() {
        when(tenantAiConfigService.getCurrentTenantId()).thenReturn(1L);
        when(learningEventMapper.insert(any(AiLearningEventPO.class))).thenAnswer(inv -> {
            AiLearningEventPO po = inv.getArgument(0);
            po.setId(1L);
            return 1;
        });

        AiLearningEventSaveDTO dto = new AiLearningEventSaveDTO();
        dto.setIntent("COMPOSE_CHAIN");
        dto.setFeature("userRegister");
        dto.setValidatePassed(true);
        dto.setValidateRounds(1);
        dto.setAdopted(true);
        dto.setHttpMode(1);

        var vo = service.record(dto);
        assertThat(vo.getPromotionEligible()).isTrue();
        assertThat(vo.getPromotionScore().doubleValue()).isGreaterThanOrEqualTo(0.97);
    }
}
