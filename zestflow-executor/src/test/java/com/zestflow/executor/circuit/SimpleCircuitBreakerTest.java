package com.zestflow.executor.circuit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCircuitBreakerTest {

    @Test
    void initialStateIsClosed() {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker("test", 3, 5000);
        assertTrue(cb.tryAcquire());
        assertEquals(CircuitState.CLOSED, cb.getState());
    }

    @Test
    void opensAfterThreshold() {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker("test", 3, 5000);
        assertTrue(cb.tryAcquire());
        cb.onFailure();
        assertTrue(cb.tryAcquire());
        cb.onFailure();
        assertTrue(cb.tryAcquire());
        cb.onFailure();

        assertFalse(cb.tryAcquire());
        assertEquals(CircuitState.OPEN, cb.getState());
    }

    @Test
    void resetsAfterSuccess() {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker("test", 3, 5000);
        cb.onFailure();
        cb.onFailure();
        cb.onSuccess();

        assertTrue(cb.tryAcquire());
        assertEquals(CircuitState.CLOSED, cb.getState());
    }

    @Test
    void halfOpenAfterCooldown() throws InterruptedException {
        SimpleCircuitBreaker cb = new SimpleCircuitBreaker("test", 1, 100);
        cb.onFailure();
        assertFalse(cb.tryAcquire());

        Thread.sleep(150);
        assertTrue(cb.tryAcquire());
        assertEquals(CircuitState.HALF_OPEN, cb.getState());
    }
}
