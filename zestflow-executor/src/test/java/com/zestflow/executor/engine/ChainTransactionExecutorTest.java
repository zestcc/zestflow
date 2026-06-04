package com.zestflow.executor.engine;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainTransactionExecutorTest {

    @Test
    void noopExecutorSkipsWhenNoManager() {
        ChainTransactionExecutor executor = ChainTransactionExecutor.noop();
        assertFalse(executor.isAvailable());
        AtomicBoolean ran = new AtomicBoolean(false);
        executor.execute("REQUIRED", () -> {
            ran.set(true);
            return null;
        });
        assertTrue(ran.get());
    }

    @Test
    void executeRollsBackOnRuntimeException() {
        AtomicReference<TransactionStatus> statusRef = new AtomicReference<>();
        PlatformTransactionManager tm = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                SimpleTransactionStatus status = new SimpleTransactionStatus();
                statusRef.set(status);
                return status;
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        ChainTransactionExecutor executor = new ChainTransactionExecutor(tm);

        try {
            executor.execute("REQUIRED", () -> {
                throw new IllegalStateException("boom");
            });
        } catch (IllegalStateException ignored) {
            // expected
        }

        assertTrue(statusRef.get().isRollbackOnly());
    }

    @Test
    void markRollbackOnlyDoesNotThrowWhenInactive() {
        ChainTransactionExecutor executor = ChainTransactionExecutor.noop();
        executor.markRollbackOnly();
    }
}
