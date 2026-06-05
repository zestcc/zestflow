package com.zestflow.executor.chain;

import com.zestflow.executor.registry.ExecutorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

/**
 * 启动时将 {@link ZestChain} 声明同步为 DB 占位链（status=未设计）。
 */
@Slf4j
@RequiredArgsConstructor
public class ChainDeclarationSyncService implements ApplicationRunner, Ordered {

    private final ChainDeclarationScanner declarationScanner;
    private final ChainDeclarationRegistry declarationRegistry;
    private final ChainRepository chainRepository;
    private final ExecutorChainProperties chainProperties;
    private final ExecutorProperties executorProperties;

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!chainProperties.isDeclarationSyncEnabled()) {
            log.info("链声明占位同步已关闭 zestflow.executor.chain.declaration-sync-enabled=false");
            return;
        }
        var declarations = declarationScanner.getDeclarations();
        declarationRegistry.replaceAll(declarations);
        if (declarations.isEmpty()) {
            return;
        }
        String appCode = executorProperties.getAppCode();
        int created = 0;
        int existing = 0;
        for (ChainDeclarationMeta meta : declarations) {
            ChainRepository.UpsertDeclarationResult result = chainRepository.upsertDeclaration(
                    appCode, meta.getChainKey(), meta.getName(), meta.getDescription(), appCode);
            if (result.created()) {
                created++;
            } else {
                existing++;
            }
        }
        log.info("链声明占位同步完成 appCode={} declared={} created={} existing={}",
                appCode, declarations.size(), created, existing);
    }
}
