package com.zestflow.admin.controller;

import com.zestflow.admin.service.log.ExecutionLiveStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogLiveStreamControllerTest {

    @Mock
    private ExecutionLiveStreamService executionLiveStreamService;

    private LogLiveStreamController controller;

    @BeforeEach
    void setUp() {
        controller = new LogLiveStreamController(executionLiveStreamService);
    }

    @Test
    void streamExecution_delegatesToService() {
        SseEmitter emitter = new SseEmitter();
        when(executionLiveStreamService.stream("exec-1", "demo-app")).thenReturn(emitter);

        SseEmitter result = controller.streamExecution("exec-1", "demo-app");

        assertThat(result).isSameAs(emitter);
        verify(executionLiveStreamService).stream("exec-1", "demo-app");
    }

    @Test
    void streamExecution_passesNullAppCode() {
        SseEmitter emitter = new SseEmitter();
        when(executionLiveStreamService.stream("exec-2", null)).thenReturn(emitter);

        assertThat(controller.streamExecution("exec-2", null)).isSameAs(emitter);
        verify(executionLiveStreamService).stream("exec-2", null);
    }
}
