package com.zestflow.executor.chain;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.common.model.dto.ChainDefinitionDTO;
import com.zestflow.common.model.dto.ChainSyncDTO;
import com.zestflow.executor.registry.AdminClient;
import com.zestflow.executor.registry.ExecutorProperties;
import com.zestflow.executor.scanner.ComponentScanner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 链加载器：在 Executor 注册成功后，从 Admin 拉取链定义并构建。
 * <p>
 * 执行流程：
 * <ol>
 *   <li>等待注册成功（最多重试 3 次）</li>
 *   <li>从 Admin 获取模块下活跃链编码列表</li>
 *   <li>逐个拉取链定义（graph_data JSON）</li>
 *   <li>校验环检测、组件存在性、配置合法性</li>
 *   <li>构建运行时 ChainDefinition 并加载到 ChainManager</li>
 *   <li>通过 Admin 同步加载状态</li>
 * </ol>
 */
@Slf4j
@RequiredArgsConstructor
public class ChainLoader implements ApplicationRunner, Ordered {

    private final AdminClient adminClient;
    private final ChainManager chainManager;
    private final ComponentScanner componentScanner;
    private final ChainValidator chainValidator;
    private final ChainDefinitionBuilder chainDefinitionBuilder;
    private final ExecutorProperties properties;

    /**
     * 在 ExecutorRegistrar 之后执行
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 10;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("链加载器开始执行...");
        loadAllChains();
    }

    /**
     * 全量加载所有链（启动时调用）
     */
    public boolean loadAllChains() {
        try {
            // 1. 等待注册成功（最多 3 次，每次 5 秒）
            boolean registered = waitForRegistration();
            if (!registered) {
                log.warn("注册尚未完成，链加载将在后台异步重试");
                asyncRetryLoad();
                return false;
            }

            // 2. 从 Admin 获取活跃链列表
            String moduleCode = resolveModuleCode();
            List<String> chainCodes = adminClient.fetchActiveChainCodes(moduleCode);
            log.info("从 Admin 获取到的活跃链: moduleCode={} count={} codes={}",
                    moduleCode, chainCodes.size(), chainCodes);

            if (chainCodes.isEmpty()) {
                log.info("没有需要加载的链 moduleCode={}", moduleCode);
                notifySync(moduleCode, List.of(), "READY");
                return true;
            }

            // 3. 逐个拉取链定义并构建
            List<ChainDefinition> definitions = loadChainDefinitions(chainCodes, moduleCode);

            // 4. 校验全部链定义
            if (!chainValidator.validateAll(definitions)) {
                log.error("部分链定义校验失败，拒绝加载");
                notifySync(moduleCode, chainCodes, "FAILED");
                return false;
            }

            // 5. 加载到 ChainManager（原子替换）
            chainManager.reload(definitions);

            // 6. 通知 Admin 加载完成
            notifySync(moduleCode, chainCodes, "READY");

            log.info("链加载完成 moduleCode={} loaded={}/{}",
                    moduleCode, definitions.size(), chainCodes.size());
            return true;

        } catch (Exception e) {
            log.error("链加载异常", e);
            return false;
        }
    }

    /**
     * 热更新指定链
     */
    public boolean reloadChain(String chainCode) {
        try {
            String moduleCode = resolveModuleCode();
            log.info("开始热更新链 code={} moduleCode={}", chainCode, moduleCode);

            ChainDefinitionDTO dto = adminClient.fetchChainDefinition(chainCode);
            if (dto == null) {
                log.warn("热更新：Admin 返回的链定义为 null code={}", chainCode);
                return false;
            }

            ChainDefinition definition = chainDefinitionBuilder.build(dto);
            List<String> errors = chainValidator.validate(definition);
            if (!errors.isEmpty()) {
                log.error("热更新：链定义校验失败 code={} errors={}", chainCode, errors);
                return false;
            }

            chainManager.load(definition);
            log.info("热更新成功 code={} version={}", chainCode, definition.getVersion());
            return true;

        } catch (Exception e) {
            log.error("热更新失败 code={}", chainCode, e);
            return false;
        }
    }

    /**
     * 批量加载链定义
     */
    private List<ChainDefinition> loadChainDefinitions(List<String> chainCodes, String moduleCode) {
        List<ChainDefinition> definitions = new ArrayList<>();

        for (String code : chainCodes) {
            try {
                log.debug("拉取链定义: code={}", code);
                ChainDefinitionDTO dto = adminClient.fetchChainDefinition(code);
                if (dto == null) {
                    log.warn("链定义为空，跳过 code={}", code);
                    continue;
                }

                ChainDefinition definition = chainDefinitionBuilder.build(dto);
                definitions.add(definition);
                log.info("链定义构建成功 code={} nodes={} layers={}",
                        code, definition.nodeCount(), definition.layerCount());

            } catch (Exception e) {
                log.error("链构建失败 code={}", code, e);
            }
        }

        return definitions;
    }

    /**
     * 等待注册完成（循环探测）
     */
    private boolean waitForRegistration() throws InterruptedException {
        for (int i = 0; i < properties.getChainLoadRetryTimes(); i++) {
            if (chainManager.activeCount() > 0) {
                // 已有链定义，无需等待
                return true;
            }
            TimeUnit.MILLISECONDS.sleep(properties.getChainLoadRetryIntervalMs());
        }
        return false;
    }

    private void asyncRetryLoad() {
        Thread retryThread = new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(30);
                log.info("后台重试链加载...");
                loadAllChains();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "zestflow-chain-retry");
        retryThread.setDaemon(true);
        retryThread.start();
    }

    private void notifySync(String moduleCode, List<String> chainCodes, String status) {
        try {
            String executorId = String.format("%s@%s:%d",
                    moduleCode, properties.getHost(), properties.getPort());
            ChainSyncDTO sync = ChainSyncDTO.builder()
                    .executorId(executorId)
                    .loadedChains(chainCodes)
                    .status(status)
                    .build();
            adminClient.notifyChainSync(sync);
        } catch (Exception e) {
            log.warn("通知 Admin 链加载状态失败", e);
        }
    }

    private String resolveModuleCode() {
        return properties.getModuleCode() != null && !properties.getModuleCode().isEmpty()
                ? properties.getModuleCode()
                : "default";
    }
}
