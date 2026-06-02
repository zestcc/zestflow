package com.zestflow.admin.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MybatisPlusConfigTest {

    @Test
    void isExcludedFromTenantLine_handlesBackticksAndSchema() {
        assertThat(MybatisPlusConfig.isExcludedFromTenantLine("`tenant`")).isTrue();
        assertThat(MybatisPlusConfig.isExcludedFromTenantLine("zestflow_admin.tenant")).isTrue();
        assertThat(MybatisPlusConfig.isExcludedFromTenantLine("TENANT")).isTrue();
        assertThat(MybatisPlusConfig.isExcludedFromTenantLine("playground_scene")).isFalse();
        assertThat(MybatisPlusConfig.isExcludedFromTenantLine("tenant_ip_mapping")).isTrue();
    }
}
