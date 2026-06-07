package com.zestflow.admin.alert;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionSlaAlertServiceTest {

    @Mock private CollectorSlaTriggerService collectorSlaTriggerService;
    @InjectMocks private ExecutionSlaAlertService service;

    @Test
    void scan_delegatesToCollector() {
        when(collectorSlaTriggerService.triggerScan()).thenReturn("scopes=1 alerts=0 emails=0");
        assertThat(service.scan()).contains("scopes=1");
        verify(collectorSlaTriggerService).triggerScan();
    }
}
