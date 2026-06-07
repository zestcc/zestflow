package com.zestflow.admin.config;

import com.zestflow.admin.model.entity.SysConfigPO;
import com.zestflow.admin.repository.SysConfigMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformConfigReaderTest {

    @Mock private SysConfigMapper sysConfigMapper;

    @Test
    void getBoolean_dbOverridesYaml() {
        SysConfigPO po = new SysConfigPO();
        po.setConfigKey(SysConfigKeys.AI_ENABLED);
        po.setConfigValue("false");
        po.setStatus(1);
        when(sysConfigMapper.selectList(any())).thenReturn(List.of(po));

        PlatformConfigReader reader = new PlatformConfigReader(sysConfigMapper);
        reader.reload();

        assertThat(reader.getBoolean(SysConfigKeys.AI_ENABLED, () -> true)).isFalse();
    }

    @Test
    void getInt_fallsBackToYamlWhenMissing() {
        when(sysConfigMapper.selectList(any())).thenReturn(List.of());
        PlatformConfigReader reader = new PlatformConfigReader(sysConfigMapper);
        reader.reload();

        assertThat(reader.getInt(SysConfigKeys.AI_TIMEOUT_MS, () -> 60_000)).isEqualTo(60_000);
    }
}
