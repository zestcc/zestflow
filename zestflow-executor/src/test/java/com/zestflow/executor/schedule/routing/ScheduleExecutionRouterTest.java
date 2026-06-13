package com.zestflow.executor.schedule.routing;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainExecuteRequestDTO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import com.zestflow.common.model.dto.PeerExecutorDTO;
import com.zestflow.executor.http.ChainExecuteFacade;
import com.zestflow.executor.registry.AdminClient;
import com.zestflow.executor.registry.ExecutorProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleExecutionRouterTest {

    @Mock
    private AdminClient adminClient;
    @Mock
    private ChainExecuteFacade chainExecuteFacade;
    @Mock
    private RemoteScheduleExecutorClient remoteClient;

    private ExecutorProperties properties;
    private ScheduleExecutionRouter router;

    @BeforeEach
    void setUp() {
        properties = new ExecutorProperties();
        properties.setAppCode("demo-app");
        properties.setHost("127.0.0.1");
        properties.setPort(20550);
        ScheduleRouteStrategyRegistry registry = new ScheduleRouteStrategyRegistry(
                List.of(new RoundRobinScheduleRouteStrategy(),
                        new HashScheduleRouteStrategy(),
                        new RandomScheduleRouteStrategy()));
        router = new ScheduleExecutionRouter(adminClient, chainExecuteFacade, remoteClient, registry, properties);
    }

    @Test
    void localStrategyExecutesInProcess() {
        ChainExecuteResultDTO success = success();
        when(chainExecuteFacade.executeCore(any())).thenReturn(success);

        ScheduleExecutionRouter.RoutedExecution routed = router.execute(
                "local", "CHN001", request("CHN001"));

        assertThat(routed.getResult().isSuccess()).isTrue();
        assertThat(routed.getAttempted()).isEqualTo(1);
        verify(chainExecuteFacade).executeCore(any());
        verify(adminClient, never()).fetchOnlinePeers(any());
    }

    @Test
    void roundRobinRoutesToRemotePeerWithFailover() {
        PeerExecutorDTO self = peer("demo-app@127.0.0.1:20550", "127.0.0.1", 20550);
        PeerExecutorDTO remote = peer("demo-app@10.0.0.2:20550", "10.0.0.2", 20550);
        when(adminClient.fetchOnlinePeers("demo-app")).thenReturn(List.of(self, remote));
        when(remoteClient.execute(eq("10.0.0.2"), eq(20550), any())).thenReturn(failure("down"));
        when(chainExecuteFacade.executeCore(any())).thenReturn(success());

        ScheduleExecutionRouter.RoutedExecution routed = router.execute(
                "round_robin", "CHN001", request("CHN001"));

        assertThat(routed.getResult().isSuccess()).isTrue();
        assertThat(routed.getAttempted()).isEqualTo(2);
        verify(remoteClient).execute(eq("10.0.0.2"), eq(20550), any());
        verify(chainExecuteFacade).executeCore(any());
    }

    @Test
    void fallsBackToLocalWhenPeersUnavailable() {
        when(adminClient.fetchOnlinePeers("demo-app")).thenReturn(List.of());
        when(chainExecuteFacade.executeCore(any())).thenReturn(success());

        ScheduleExecutionRouter.RoutedExecution routed = router.execute(
                "round_robin", "CHN001", request("CHN001"));

        assertThat(routed.getResult().isSuccess()).isTrue();
        verify(chainExecuteFacade).executeCore(any());
    }

    private static ChainExecuteRequestDTO request(String chainCode) {
        return ChainExecuteRequestDTO.builder().chainCode(chainCode).build();
    }

    private static PeerExecutorDTO peer(String id, String host, int port) {
        return PeerExecutorDTO.builder()
                .executorId(id)
                .appCode("demo-app")
                .host(host)
                .port(port)
                .build();
    }

    private static ChainExecuteResultDTO success() {
        ChainExecuteResultDTO dto = new ChainExecuteResultDTO();
        dto.setStatus(ChainConstants.CHAIN_SUCCESS);
        return dto;
    }

    private static ChainExecuteResultDTO failure(String message) {
        ChainExecuteResultDTO dto = new ChainExecuteResultDTO();
        dto.setStatus(ChainConstants.CHAIN_FAILED);
        dto.setErrorMessage(message);
        return dto;
    }
}
