package com.zestflow.admin.service.sso.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zestflow.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZestSsoProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void parseLogoutUrlResponse_success() throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("code", 0, "data", "http://localhost:9000/connect/logout?x=1"));

        String url = ZestSsoProvider.parseLogoutUrlResponse(body, objectMapper);

        assertThat(url).contains("connect/logout");
    }

    @Test
    void parseLogoutUrlResponse_nonZeroCode_throws() {
        assertThatThrownBy(() -> ZestSsoProvider.parseLogoutUrlResponse(
                "{\"code\":400,\"message\":\"bad\"}", objectMapper))
                .isInstanceOf(BizException.class);
    }

    @Test
    void parseLogoutUrlResponse_missingData_throws() {
        assertThatThrownBy(() -> ZestSsoProvider.parseLogoutUrlResponse(
                "{\"code\":0}", objectMapper))
                .isInstanceOf(BizException.class);
    }
}
