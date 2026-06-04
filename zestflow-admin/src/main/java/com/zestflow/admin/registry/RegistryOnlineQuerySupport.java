package com.zestflow.admin.registry;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.common.constant.RegistryConstants;

import java.util.List;
import java.util.Set;

/** 在线执行器查询 — DB 元数据 + 内存/Redis 存活表交集（对标 Nacos 健康实例过滤）。 */
public final class RegistryOnlineQuerySupport {

    private RegistryOnlineQuerySupport() {
    }

    public static List<ExecutorRegistryPO> listLiveOnlineExecutors(ExecutorRegistryMapper mapper,
                                                                  RegistryLiveStore liveStore,
                                                                  String appCode) {
        Set<String> aliveIds = liveStore.aliveExecutorIds();
        if (aliveIds.isEmpty()) {
            return List.of();
        }
        LambdaQueryWrapper<ExecutorRegistryPO> wrapper = new LambdaQueryWrapper<ExecutorRegistryPO>()
                .eq(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE)
                .in(ExecutorRegistryPO::getExecutorId, aliveIds);
        if (appCode != null && !appCode.isBlank()) {
            wrapper.eq(ExecutorRegistryPO::getAppCode, appCode);
        }
        return mapper.selectList(wrapper);
    }
}
