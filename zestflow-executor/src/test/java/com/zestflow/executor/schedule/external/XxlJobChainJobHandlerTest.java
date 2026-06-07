package com.zestflow.executor.schedule.external;

import com.xxl.job.core.context.XxlJobHelper;
import com.zestflow.executor.http.ChainExecuteFacade;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class XxlJobChainJobHandlerTest {

    @Mock private ChainExecuteFacade chainExecuteFacade;
    private XxlJobChainJobHandler handler;

    @BeforeEach
    void setUp() {
        XxlJobScheduleProperties props = new XxlJobScheduleProperties();
        handler = new XxlJobChainJobHandler(chainExecuteFacade, props);
    }

    @Test
    void executeChain_invokesFacade() {
        handler.executeChain("demo-chain");
        ArgumentCaptor<ChainExecuteRequestDTO> captor = ArgumentCaptor.forClass(ChainExecuteRequestDTO.class);
        verify(chainExecuteFacade).executeCore(captor.capture());
        assertThat(captor.getValue().getChainCode()).isEqualTo("demo-chain");
        assertThat(captor.getValue().getSource()).isEqualTo("xxl-job");
    }
}
