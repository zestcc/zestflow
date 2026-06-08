package com.zestflow.admin.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiConfigTest {

    @Test
    void openApiBeanContainsAdminPrefixAndSecuritySchemes() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI api = config.zestFlowOpenApi("zestflow-admin");

        assertThat(api.getInfo().getTitle()).isEqualTo("ZestFlow Admin API");
        assertThat(api.getInfo().getDescription()).contains("/api/zestflow");
        assertThat(api.getComponents().getSecuritySchemes()).containsKeys("bearer-jwt", "registry-token");
    }
}
