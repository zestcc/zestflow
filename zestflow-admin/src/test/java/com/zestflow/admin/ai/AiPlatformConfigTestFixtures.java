package com.zestflow.admin.ai;

import com.zestflow.admin.config.AiPlatformConfig;
import com.zestflow.admin.config.PlatformConfigReader;
import com.zestflow.admin.repository.SysConfigMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 单测：无 DB 时 AiPlatformConfig 回退 yaml */
public final class AiPlatformConfigTestFixtures {

    private AiPlatformConfigTestFixtures() {
    }

    public static AiPlatformConfig fromYaml(AiProperties yaml) {
        SysConfigMapper mapper = mock(SysConfigMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        PlatformConfigReader reader = new PlatformConfigReader(mapper);
        reader.reload();
        return new AiPlatformConfig(reader, yaml);
    }
}
