package com.zestflow.executor.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NettyMvcDispatcherTest {

    @Mock
    private RequestMappingHandlerMapping mapping;

    @Mock
    private RequestMappingHandlerAdapter adapter;

    @Test
    void isDispatchableBusinessPath_allowsBusinessApi() {
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/api/orders/handle")).isTrue();
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/api/orders/handle?id=1")).isTrue();
    }

    @Test
    void isDispatchableBusinessPath_rejectsFrameworkRoutes() {
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/api/chains")).isFalse();
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/api/chains/CHN001")).isFalse();
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/api/designs/DSN001")).isFalse();
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/api/components")).isFalse();
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/api/endpoints")).isFalse();
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/api/endpoints/classes")).isFalse();
    }

    @Test
    void isDispatchableBusinessPath_rejectsNonApi() {
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/execute")).isFalse();
        assertThat(NettyMvcDispatcher.isDispatchableBusinessPath("/health")).isFalse();
    }

    @Test
    void dispatch_nullMapping_returnsNotFound() throws Exception {
        NettyMvcDispatcher dispatcher = new NettyMvcDispatcher(null, null, List.of());
        NettyMvcDispatcher.DispatchResult result =
                dispatcher.dispatch("POST", "/api/orders/demo", "{}");
        assertThat(result.handled()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(404);
    }

    @Test
    void dispatch_handlerNotFound_returnsNotFound() throws Exception {
        when(mapping.getHandler(any(MockHttpServletRequest.class))).thenReturn(null);
        NettyMvcDispatcher dispatcher = new NettyMvcDispatcher(mapping, adapter, List.of());
        NettyMvcDispatcher.DispatchResult result =
                dispatcher.dispatch("POST", "/api/orders/missing", "{}");
        assertThat(result.handled()).isFalse();
    }

    @Test
    void dispatch_outsideScanPackages_returnsForbidden() throws Exception {
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getBeanType()).thenReturn((Class) OutsideController.class);
        when(mapping.getHandler(any(MockHttpServletRequest.class)))
                .thenReturn(new HandlerExecutionChain(handlerMethod));

        NettyMvcDispatcher dispatcher = new NettyMvcDispatcher(
                mapping, adapter, List.of("com.zestflow.demo.controller"));

        NettyMvcDispatcher.DispatchResult result =
                dispatcher.dispatch("POST", "/api/orders/demo", "{}");

        assertThat(result.handled()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(403);
        assertThat(result.body()).contains("不在允许范围内");
    }

    @Test
    void dispatch_matchingScanPackage_invokesAdapter() throws Exception {
        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getBeanType()).thenReturn((Class) InsideController.class);
        when(mapping.getHandler(any(MockHttpServletRequest.class)))
                .thenReturn(new HandlerExecutionChain(handlerMethod));
        doAnswer(invocation -> {
            MockHttpServletResponse response = invocation.getArgument(1);
            response.setStatus(200);
            response.getWriter().write("{\"code\":200}");
            return null;
        }).when(adapter).handle(any(), any(), any());

        NettyMvcDispatcher dispatcher = new NettyMvcDispatcher(
                mapping, adapter, List.of("com.zestflow.executor.server"));

        NettyMvcDispatcher.DispatchResult result =
                dispatcher.dispatch("POST", "/api/orders/demo", "{\"id\":1}");

        assertThat(result.handled()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(result.body()).contains("200");
    }

    /** 模拟 scan-packages 白名单外的 Controller */
    static class OutsideController {
        void handle() {
        }
    }

    /** 模拟白名单内的 Controller（与测试类同包） */
    static class InsideController {
        void handle() {
        }
    }
}
