package com.zestflow.admin.schedule;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.common.model.dto.ChainExecuteResultDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleExecutorFailoverTest {

    @Mock
    private ExecutorClient executorClient;

    @Mock
    private RouteStrategy routeStrategy;

    private ExecutorRegistryPO primary;
    private ExecutorRegistryPO backup;

    @BeforeEach
    void setUp() {
        primary = executor("exec-a", "host-a", 20550);
        backup = executor("exec-b", "host-b", 20550);
        when(routeStrategy.name()).thenReturn("round_robin");
    }

    @Test
    void executeWithFailover_succeedsOnPrimary() {
        when(routeStrategy.select(any(), eq("chain-1"))).thenReturn(primary);
        when(executorClient.execute("host-a", 20550, "chain-1", Map.of()))
                .thenReturn(successResult());

        ScheduleExecutorFailover.FailoverResult result = ScheduleExecutorFailover.executeWithFailover(
                List.of(primary, backup), routeStrategy, "chain-1", Map.of(), executorClient);

        assertThat(ScheduleExecutorFailover.isSuccess(result.getResult())).isTrue();
        assertThat(result.getExecutor().getExecutorId()).isEqualTo("exec-a");
        assertThat(result.getAttempted()).isEqualTo(1);
        verify(executorClient, times(1)).execute(any(), any(Integer.class), any(), any());
    }

    @Test
    void executeWithFailover_fallsBackToSecondExecutor() {
        when(routeStrategy.select(any(), eq("chain-1"))).thenReturn(primary);
        when(executorClient.execute("host-a", 20550, "chain-1", Map.of()))
                .thenReturn(failureResult("primary down"));
        when(executorClient.execute("host-b", 20550, "chain-1", Map.of()))
                .thenReturn(successResult());

        ScheduleExecutorFailover.FailoverResult result = ScheduleExecutorFailover.executeWithFailover(
                List.of(primary, backup), routeStrategy, "chain-1", Map.of(), executorClient);

        assertThat(ScheduleExecutorFailover.isSuccess(result.getResult())).isTrue();
        assertThat(result.getExecutor().getExecutorId()).isEqualTo("exec-b");
        assertThat(result.getAttempted()).isEqualTo(2);
    }

    @Test
    void orderWithPrimaryFirst_putsPrimaryFirstThenSortedRest() {
        ExecutorRegistryPO c = executor("exec-c", "host-c", 1);
        List<ExecutorRegistryPO> ordered = ScheduleExecutorFailover.orderWithPrimaryFirst(
                List.of(backup, c, primary), primary);

        assertThat(ordered).extracting(ExecutorRegistryPO::getExecutorId)
                .containsExactly("exec-a", "exec-b", "exec-c");
    }

    private static ChainExecuteResultDTO successResult() {
        ChainExecuteResultDTO dto = new ChainExecuteResultDTO();
        dto.setStatus(ScheduleExecutorFailover.EXECUTE_SUCCESS);
        return dto;
    }

    private static ChainExecuteResultDTO failureResult(String message) {
        ChainExecuteResultDTO dto = new ChainExecuteResultDTO();
        dto.setStatus(2);
        dto.setErrorMessage(message);
        return dto;
    }

    private static ExecutorRegistryPO executor(String id, String host, int port) {
        ExecutorRegistryPO po = new ExecutorRegistryPO();
        po.setExecutorId(id);
        po.setExecutorHost(host);
        po.setExecutorPort(port);
        return po;
    }
}
