package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.design.DesignPO;
import com.zestflow.executor.design.DesignRepository;
import com.zestflow.executor.design.DesignStatus;
import com.zestflow.common.model.dto.ChainSyncDTO;
import com.zestflow.executor.engine.NodeRunner;
import org.springframework.beans.factory.ObjectProvider;
import com.zestflow.executor.registry.AdminClient;
import com.zestflow.executor.registry.ExecutorProperties;
import com.zestflow.executor.route.ChainRouteRegistry;
import com.zestflow.executor.scanner.ComponentScanner;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.jdbc.CannotGetJdbcConnectionException;

import java.util.ArrayList;
import java.util.List;

/**
 * 链加载器：启动时从本地 DB 读取链定义并构建运行时 ChainDefinition。
 * <p>
 * Admin 不负责提供链数据。Executor 启动时直接从本地 zf_chain、zf_design 表
 * 中读取所有已绑定设计的链，解析 graph_data JSON 为 DAG 运行模型。
 * 发布热更新由 Admin 推送指令 + graphData 触发 reloadChainLocal()。
 */
@Slf4j
@RequiredArgsConstructor
public class ChainLoader implements ApplicationRunner, Ordered {

    private final ChainManager chainManager;
    private final ComponentScanner componentScanner;
    private final ChainValidator chainValidator;
    private final ChainDefinitionBuilder chainDefinitionBuilder;
    private final ChainRepository chainRepo;
    private final DesignRepository designRepo;
    private final NodeRunner nodeRunner;
    private final AdminClient adminClient;
    private final ExecutorProperties executorProperties;
    private final ObjectProvider<ChainRouteRegistry> chainRouteRegistryProvider;

    @Data
    @AllArgsConstructor
    public static class ChainReloadResult {
        private boolean success;
        private String errorMessage;
        private int nodeCount;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("链加载器启动——从本地 DB 加载所有可执行链");
        loadAllChains();
    }

    /**
     * 全量加载所有链（启动时调用）
     * <p>
     * 从本地 zf_chain 表读取启用的链，逐条绑定设计后构建
     * ChainDefinition 并加载到 ChainManager。
     */
    public boolean loadAllChains() {
        try {
            // 1. 查询所有未停用的链
            List<ChainPO> chains = chainRepo.list(null, null);
            if (chains.isEmpty()) {
                log.info("本地无链数据，跳过加载");
                return true;
            }

            // 2. 只加载已发布或发布中的链（status >= 3），未发布的链不注入运行时
            List<ChainPO> activeChains = chains.stream()
                    .filter(c -> c.getStatus() != null && c.getStatus() >= 3)
                    .filter(c -> c.getDesignCode() != null && !c.getDesignCode().isEmpty())
                    .toList();

            if (activeChains.isEmpty()) {
                log.info("无可加载的活跃链（共 {} 条，均无绑定设计或已停用）", chains.size());
                return true;
            }

            // 3. 逐条构建 ChainDefinition
            List<ChainDefinition> definitions = new ArrayList<>();
            for (ChainPO chain : activeChains) {
                try {
                    DesignPO design = designRepo.get(chain.getDesignCode());
                    if (design == null || (design.getGraphData() == null && design.getChainData() == null)) {
                        log.warn("设计数据为空，跳过 chainCode={} designCode={}", chain.getCode(), chain.getDesignCode());
                        continue;
                    }

                    ChainDefinition definition = chainDefinitionBuilder.build(
                            chain.getCode(), chain.getVersion(),
                            design.getChainData(), design.getGraphData());
                    definitions.add(definition);

                    log.info("链定义构建成功 code={} nodes={} layers={}",
                            chain.getCode(), definition.nodeCount(), definition.layerCount());
                } catch (Exception e) {
                    log.error("链构建失败 code={}", chain.getCode(), e);
                }
            }

            if (definitions.isEmpty()) {
                log.warn("所有活跃链均构建失败");
                return false;
            }

            // 4. 校验全部链定义
            if (!chainValidator.validateAll(definitions)) {
                log.error("部分链定义校验失败，拒绝加载");
                return false;
            }

            // 5. 加载到 ChainManager
            chainManager.reload(definitions);
            refreshHttpRoutes();

            // 6. 通知 Admin 同步状态
            notifyAdminSync(definitions, "READY", null);

            log.info("链加载完成 loaded={}/{}", definitions.size(), activeChains.size());
            return true;

        } catch (CannotGetJdbcConnectionException e) {
            log.warn("链加载跳过：Executor 数据源不可用（{}）。同库模式请删除 zestflow.executor.datasource 配置以复用 spring.datasource；"
                            + "分库模式请创建对应库并执行 zestflow 表结构迁移",
                    rootMessage(e));
            return false;
        } catch (Exception e) {
            log.warn("链加载异常（应用继续运行）: {}", rootMessage(e));
            return false;
        }
    }

    /**
     * 从本地 DB 热加载链定义（Admin 推送发布时调用）
     * <p>
     * 如果入参携带 graphData，先持久化到设计表，再从本地 DB 读取构建；
     * 否则直接从本地 DB 读取最新数据构建。会递增版本并保存快照。
     */
    public ChainReloadResult reloadChainLocal(String chainCode, String graphData, String chainData) {
        return reloadChainInternal(chainCode, graphData, chainData, true, true);
    }

    /**
     * 轮询热加载 — 仅重建内存 ChainDefinition，不递增 DB 版本（对标 LiteFlow/Nacos 配置刷新）。
     */
    public ChainReloadResult reloadFromDatabase(String chainCode) {
        return reloadChainInternal(chainCode, null, null, false, false);
    }

    /** 解析链展示名称，不存在或 DB 不可用时回退为链编码 */
    public String resolveChainDisplayName(String chainCode) {
        if (chainCode == null || chainCode.isEmpty()) {
            return chainCode;
        }
        try {
            ChainPO chain = chainRepo.get(chainCode);
            if (chain != null && chain.getName() != null && !chain.getName().isEmpty()) {
                return chain.getName();
            }
        } catch (Exception e) {
            log.debug("解析链展示名称失败，回退 chainCode={}", chainCode, e);
        }
        return chainCode;
    }

    private ChainReloadResult reloadChainInternal(String chainCode, String graphData, String chainData,
                                                   boolean persistGraph, boolean incrementVersion) {
        try {
            // 1. 检查链是否存在
            ChainPO chain = chainRepo.get(chainCode);
            if (chain == null) {
                return new ChainReloadResult(false, "链不存在: " + chainCode, 0);
            }
            if (chain.getStatus() == null || chain.getStatus() == 0) {
                return new ChainReloadResult(false, "链已停用: " + chainCode, 0);
            }

            // 2. 检查设计绑定
            String designCode = chain.getDesignCode();
            if (designCode == null || designCode.isEmpty()) {
                return new ChainReloadResult(false, "链未绑定设计: " + chainCode, 0);
            }

            // 3. 如果推送了图形数据，先持久化到设计表
            if (persistGraph && graphData != null && !graphData.isEmpty()) {
                designRepo.saveGraph(designCode, graphData, chainData, null);
                log.info("推送 graphData/chainData 已持久化 designCode={}", designCode);
            }

            // 4. 读取设计，获取 graphData
            DesignPO design = designRepo.get(designCode);
            if (design == null) {
                return new ChainReloadResult(false, "设计不存在: " + designCode, 0);
            }
            if (design.getStatus() == null || design.getStatus() != DesignStatus.ENABLED) {
                return new ChainReloadResult(false, "关联设计未启用: " + designCode, 0);
            }
            String actualGraphData = design.getGraphData();
            String actualChainData = design.getChainData();
            if ((actualGraphData == null || actualGraphData.isEmpty()) && (actualChainData == null || actualChainData.isEmpty())) {
                return new ChainReloadResult(false, "设计图为空: " + designCode, 0);
            }

            // 5. 从 JSON 构建链定义（优先使用 chainData）
            int version = chain.getVersion() != null ? chain.getVersion() : 1;
            ChainDefinition definition = chainDefinitionBuilder.build(chainCode,
                    version, actualChainData, actualGraphData);

            // 6. 校验
            List<String> errors = chainValidator.validate(definition);
            if (!errors.isEmpty()) {
                log.error("链定义校验失败 code={} errors={}", chainCode, errors);
                return new ChainReloadResult(false, "校验失败: " + String.join("; ", errors), 0);
            }

            // 6.5 版本化：发布热加载时递增版本并保存快照（轮询刷新跳过）
            int newVersion = version;
            if (incrementVersion) {
                try {
                    newVersion = chainRepo.incrementVersion(chainCode);
                    definition.setVersion(newVersion);
                    chainRepo.saveVersionSnapshot(chainCode, newVersion, designCode,
                            actualGraphData, actualChainData, null);
                    log.info("版本快照已保存 code={} version={}", chainCode, newVersion);
                } catch (Exception e) {
                    log.warn("保存版本快照失败（不影响热加载）code={}", chainCode, e);
                }
            }

            // 7. 加载到 ChainManager
            // 清理旧链的熔断器状态
            ChainDefinition oldDef = chainManager.get(chainCode);
            chainManager.load(definition);
            refreshHttpRoutes();
            if (oldDef != null) {
                nodeRunner.clearCircuitBreakers(oldDef.getNodes().keySet());
            }
            log.info("链热加载成功 code={} nodes={} layers={}",
                    chainCode, definition.nodeCount(), definition.layerCount());

            if (incrementVersion) {
                chainRepo.markPublished(chainCode, null);
            }

            // 通知 Admin 同步状态（轮询刷新不上报，避免 Admin 状态抖动）
            if (incrementVersion) {
                notifyAdminSync(List.of(definition), "READY", null);
            }

            return new ChainReloadResult(true, null, definition.nodeCount());

        } catch (Exception e) {
            log.error("链热加载异常 code={}", chainCode, e);
            return new ChainReloadResult(false, "加载异常: " + e.getMessage(), 0);
        }
    }

    private void refreshHttpRoutes() {
        chainRouteRegistryProvider.ifAvailable(registry -> registry.refresh(chainManager));
    }

    /**
     * 通知 Admin 链加载状态（忽略失败，不做重试）
     */
    private void notifyAdminSync(List<ChainDefinition> definitions, String status, String errorMessage) {
        if (adminClient == null) return;
        try {
            List<String> loadedCodes = definitions.stream()
                    .map(ChainDefinition::getCode)
                    .collect(java.util.stream.Collectors.toList());
            ChainSyncDTO sync = ChainSyncDTO.builder()
                    .executorId(resolveExecutorId())
                    .loadedChains(loadedCodes)
                    .status(status)
                    .errorMessage(errorMessage)
                    .timestamp(System.currentTimeMillis())
                    .build();
            adminClient.notifyChainSync(sync);
        } catch (Exception e) {
            log.debug("通知 Admin 链同步失败（忽略）: {}", e.getMessage(), e);
        }
    }

    private String resolveExecutorId() {
        return String.format("%s@%s:%d",
                executorProperties.getAppCode(),
                executorProperties.getHost(),
                executorProperties.getPort());
    }

    private static String rootMessage(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root.getMessage() != null ? root.getMessage() : e.toString();
    }

    /**
     * 校验设计翻译后的链定义是否可执行
     */
    public List<String> validateDesignFlow(String chainCode, String graphData, String chainData) {
        try {
            ChainDefinition definition = chainDefinitionBuilder.build(chainCode, 1, chainData, graphData);
            return chainValidator.validate(definition);
        } catch (Exception e) {
            log.warn("设计链定义解析失败 chainCode={}", chainCode, e);
            return List.of("链定义解析失败: " + e.getMessage());
        }
    }

    /**
     * 从内存卸载链定义（设计/链删除时调用，不影响 DB）
     */
    public void unloadFromMemory(String chainCode) {
        ChainDefinition oldDef = chainManager.get(chainCode);
        chainManager.unload(chainCode);
        refreshHttpRoutes();
        if (oldDef != null && oldDef.getNodes() != null) {
            nodeRunner.clearCircuitBreakers(oldDef.getNodes().keySet());
        }
    }
}
