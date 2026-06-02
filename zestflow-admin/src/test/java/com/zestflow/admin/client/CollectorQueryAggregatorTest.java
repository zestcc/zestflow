package com.zestflow.admin.client;

import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.common.protocol.EventQuery;
import com.zestflow.common.protocol.EventQueryResult;
import com.zestflow.common.protocol.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectorQueryAggregatorTest {

    @Mock
    private CollectorQueryClient queryClient;

    @Mock
    private CollectorRegistryService collectorRegistryService;

    private CollectorQueryAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new CollectorQueryAggregator(queryClient, collectorRegistryService);
        ReflectionTestUtils.setField(aggregator, "protocol", "http");
        ReflectionTestUtils.setField(aggregator, "fallbackApiUrl", "http://localhost:20650");
    }

    @Test
    void usesFallbackApiUrlWhenRegistryEmpty() {
        when(collectorRegistryService.listAllOnline()).thenReturn(List.of());
        EventQueryResult item = new EventQueryResult();
        item.setEventId("e1");
        when(queryClient.queryEvents(eq("http://localhost:20650"), any()))
                .thenReturn(new PageResult<>(List.of(item), 1L, 1, 10));

        PageResult<EventQueryResult> page = aggregator.queryEvents(new EventQuery(), null);

        assertThat(page.getList()).hasSize(1);
        assertThat(page.getList().get(0).getEventId()).isEqualTo("e1");
    }

    @Test
    void mergeEventPages_usesMaxShardTotalNotSum() {
        when(collectorRegistryService.listAllOnline()).thenReturn(List.of(
                CollectorRegistryVO.builder().collectorHost("h1").collectorPort(20650).build(),
                CollectorRegistryVO.builder().collectorHost("h2").collectorPort(20651).build()));

        EventQueryResult e1 = new EventQueryResult();
        e1.setEventId("e1");
        EventQueryResult e2 = new EventQueryResult();
        e2.setEventId("e2");

        when(queryClient.queryEvents(eq("http://h1:20650"), any()))
                .thenReturn(new PageResult<>(List.of(e1), 100L, 1, 10));
        when(queryClient.queryEvents(eq("http://h2:20651"), any()))
                .thenReturn(new PageResult<>(List.of(e2), 80L, 1, 10));

        PageResult<EventQueryResult> page = aggregator.queryEvents(new EventQuery(), null);

        assertThat(page.getTotal()).isEqualTo(100L);
        assertThat(page.getList()).hasSize(2);
    }
}
