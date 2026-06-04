package com.zestflow.executor.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 链编排事务执行器 — 基于 Spring {@link TransactionTemplate}，由设计器 chainData.config.transaction 驱动。
 */
@Slf4j
public class ChainTransactionExecutor {

    private final PlatformTransactionManager transactionManager;
    /** TransactionTemplate 无 AOP 上下文，需 ThreadLocal 传递 status 供 markRollbackOnly */
    private final ThreadLocal<TransactionStatus> currentStatusHolder = new ThreadLocal<>();

    public ChainTransactionExecutor(PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    public static ChainTransactionExecutor noop() {
        return new ChainTransactionExecutor(null);
    }

    public boolean isAvailable() {
        return transactionManager != null;
    }

    public <T> T execute(String propagationName, Supplier<T> action) {
        if (transactionManager == null) {
            log.warn("链配置了事务但 PlatformTransactionManager 不可用，跳过事务包装");
            return action.get();
        }
        TransactionTemplate template = createTemplate(propagationName);
        return template.execute(status -> {
            TransactionStatus previous = currentStatusHolder.get();
            currentStatusHolder.set(status);
            try {
                return action.get();
            } catch (RuntimeException ex) {
                status.setRollbackOnly();
                throw ex;
            } finally {
                if (previous != null) {
                    currentStatusHolder.set(previous);
                } else {
                    currentStatusHolder.remove();
                }
            }
        });
    }

    public void execute(String propagationName, Runnable action) {
        execute(propagationName, () -> {
            action.run();
            return null;
        });
    }

    /** 节点失败但未抛异常时，标记当前事务回滚 */
    public void markRollbackOnly() {
        TransactionStatus status = currentStatusHolder.get();
        if (status != null && !status.isCompleted()) {
            status.setRollbackOnly();
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        log.debug("标记事务回滚失败: 无当前 TransactionStatus");
    }

    public boolean isTransactionActive() {
        TransactionStatus status = currentStatusHolder.get();
        if (status != null && !status.isCompleted()) {
            return true;
        }
        return TransactionSynchronizationManager.isActualTransactionActive();
    }

    private TransactionTemplate createTemplate(String propagationName) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(resolvePropagationBehavior(propagationName));
        return template;
    }

    private static int resolvePropagationBehavior(String propagationName) {
        if (propagationName == null || propagationName.isBlank()) {
            return TransactionDefinition.PROPAGATION_REQUIRED;
        }
        String normalized = propagationName.trim().toUpperCase();
        return switch (normalized) {
            case "REQUIRED" -> TransactionDefinition.PROPAGATION_REQUIRED;
            case "REQUIRES_NEW" -> TransactionDefinition.PROPAGATION_REQUIRES_NEW;
            case "NESTED" -> TransactionDefinition.PROPAGATION_NESTED;
            case "SUPPORTS" -> TransactionDefinition.PROPAGATION_SUPPORTS;
            case "NOT_SUPPORTED" -> TransactionDefinition.PROPAGATION_NOT_SUPPORTED;
            case "MANDATORY" -> TransactionDefinition.PROPAGATION_MANDATORY;
            case "NEVER" -> TransactionDefinition.PROPAGATION_NEVER;
            default -> {
                log.warn("未知事务传播策略 {}，使用 REQUIRED", propagationName);
                yield TransactionDefinition.PROPAGATION_REQUIRED;
            }
        };
    }
}
