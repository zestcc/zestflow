package com.zestflow.executor.lifecycle;

import com.zestflow.common.constant.ChainConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 链状态机
 * <p>
 * 管理链执行的生命周期状态转换，使用 ReadWriteLock 保证并发安全。
 * 转换时校验合法性，非法转换会被拒绝。
 */
@Slf4j
public class ChainStateMachine {

    private volatile ChainState current = ChainState.INIT;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 转换到目标状态（使用 ChainConstants 中的状态码）
     */
    public boolean transit(int targetCode) {
        ChainState target = ChainState.fromCode(targetCode);
        return transit(target);
    }

    /**
     * 转换到目标状态
     */
    public boolean transit(ChainState target) {
        lock.writeLock().lock();
        try {
            if (!current.canTransitTo(target)) {
                log.warn("非法链状态转换: {} → {}，已忽略", current, target);
                return false;
            }
            ChainState previous = current;
            current = target;
            log.debug("链状态转换: {} → {} (code: {}→{})",
                    previous, target, previous.getCode(), target.getCode());
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取当前状态
     */
    public ChainState currentState() {
        lock.readLock().lock();
        try {
            return current;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 获取当前状态码
     */
    public int current() {
        lock.readLock().lock();
        try {
            return current.getCode();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 是否处于终止状态
     */
    public boolean isTerminated() {
        lock.readLock().lock();
        try {
            return current.isTerminal();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 是否正在运行
     */
    public boolean isRunning() {
        lock.readLock().lock();
        try {
            return current.isRunning();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 重置到初始状态
     */
    public void reset() {
        lock.writeLock().lock();
        try {
            current = ChainState.INIT;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
