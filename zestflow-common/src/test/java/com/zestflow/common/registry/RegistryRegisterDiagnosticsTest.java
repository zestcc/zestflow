package com.zestflow.common.registry;

import com.zestflow.common.model.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryRegisterDiagnosticsTest {

    @Test
    void describeResultFailure_401_mentionsToken() {
        String msg = RegistryRegisterDiagnostics.describeResultFailure(
                Result.fail(401, "Invalid registry token"));
        assertTrue(msg.contains("Token") && msg.contains("registry-token"));
    }

    @Test
    void describeResultFailure_nullResponse() {
        assertTrue(RegistryRegisterDiagnostics.describeResultFailure(null).contains("响应为空"));
    }

    @Test
    void describeException_connectionRefused() {
        String msg = RegistryRegisterDiagnostics.describeException(
                new IllegalStateException("HTTP 请求失败", new java.net.ConnectException("Connection refused")),
                false);
        assertTrue(msg.contains("无法连接 Admin") && msg.contains("admin-addresses"));
    }

    @Test
    void describeException_missingTokenHint() {
        String msg = RegistryRegisterDiagnostics.describeException(
                new IllegalStateException("HTTP 401"),
                false);
        assertTrue(msg.contains("未配置"));
    }
}
