package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.design.DesignPO;
import com.zestflow.executor.design.DesignRepository;
import com.zestflow.executor.scanner.ComponentScanner;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

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
                            chain.getCode(), ChainConstants.CHAIN_INIT,
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

            log.info("链加载完成 loaded={}/{}", definitions.size(), activeChains.size());
            return true;

        } catch (Exception e) {
            log.error("链加载异常", e);
            return false;
        }
    }

    /**
     * 从本地 DB 热加载链定义（Admin 推送发布时调用）
     * <p>
     * 如果入参携带 graphData，先持久化到设计表，再从本地 DB 读取构建；
     * 否则直接从本地 DB 读取最新数据构建。
     *
     * @param chainCode 链编码
     * @param graphData 设计图谱 JSON（可选，null 时从 DB 读取）
     * @return 加载结果
     */
    public ChainReloadResult reloadChainLocal(String chainCode, String graphData, String chainData) {
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
            if (graphData != null && !graphData.isEmpty()) {
                designRepo.saveGraph(designCode, graphData, chainData, null);
                log.info("推送 graphData/chainData 已持久化 designCode={}", designCode);
            }

            // 4. 读取设计，获取 graphData
            DesignPO design = designRepo.get(designCode);
            if (design == null) {
                return new ChainReloadResult(false, "设计不存在: " + designCode, 0);
            }
            String actualGraphData = design.getGraphData();
            String actualChainData = design.getChainData();
            if ((actualGraphData == null || actualGraphData.isEmpty()) && (actualChainData == null || actualChainData.isEmpty())) {
                return new ChainReloadResult(false, "设计图为空: " + designCode, 0);
            }

            // 5. 从 JSON 构建链定义（优先使用 chainData）
            ChainDefinition definition = chainDefinitionBuilder.build(chainCode,
                    ChainConstants.CHAIN_INIT, actualChainData, actualGraphData);

            // 6. 校验
            List<String> errors = chainValidator.validate(definition);
            if (!errors.isEmpty()) {
                log.error("链定义校验失败 code={} errors={}", chainCode, errors);
                return new ChainReloadResult(false, "校验失败: " + String.join("; ", errors), 0);
            }

            // 7. 加载到 ChainManager
            chainManager.load(definition);
            log.info("链热加载成功 code={} nodes={} layers={}",
                    chainCode, definition.nodeCount(), definition.layerCount());

            return new ChainReloadResult(true, null, definition.nodeCount());

        } catch (Exception e) {
            log.error("链热加载异常 code={}", chainCode, e);
            return new ChainReloadResult(false, "加载异常: " + e.getMessage(), 0);
        }
    }
}
