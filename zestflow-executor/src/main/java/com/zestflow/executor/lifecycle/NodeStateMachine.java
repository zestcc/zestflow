package com.zestflow.executor.lifecycle;

import com.zestflow.common.constant.ChainConstants;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 节点状态机
 * <p>
 * 管理单次节点执行的生命周期状态转换。
 */
@Slf4j
public class NodeStateMachine {

    private volatile NodeState current = NodeState.CREATED;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * 转换到目标状态
     */
    public boolean transit(int targetCode) {
        NodeState target = NodeState.fromCode(targetCode);
        return transit(target);
    }

    /**
     * 转换到目标状态
     */
    public boolean transit(NodeState target) {
        lock.writeLock().lock();
        try {
            if (!current.canTransitTo(target)) {
                log.warn("非法节点状态转换: {} → {}，已忽略", current, target);
                return false;
            }
            NodeState previous = current;
            current = target;
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取当前状态
     */
    public NodeState currentState() {
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
     * 是否终止状态
     */
    public boolean isTerminated() {
        lock.readLock().lock();
        try {
            return current.isTerminal();
        } finally {
            lock.readLock().unlock();
        }
    }
}
