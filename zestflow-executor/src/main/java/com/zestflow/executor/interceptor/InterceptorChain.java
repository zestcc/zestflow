package com.zestflow.executor.interceptor;

import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 拦截器链执行器
 * <p>
 * 管理所有注册的 ChainInterceptor 和 NodeInterceptor，按 order 排序后依次执行。
 * 一个拦截器的异常不会阻止后续拦截器的执行。
 */
@Slf4j
public class InterceptorChain {

    private final List<ChainInterceptor> chainInterceptors = new ArrayList<>();
    private final List<NodeInterceptor> nodeInterceptors = new ArrayList<>();

    /**
     * 注册链级拦截器
     */
    public synchronized void addChainInterceptor(ChainInterceptor interceptor) {
        chainInterceptors.add(interceptor);
        chainInterceptors.sort(Comparator.comparingInt(ChainInterceptor::order));
    }

    /**
     * 注册节点级拦截器
     */
    public synchronized void addNodeInterceptor(NodeInterceptor interceptor) {
        nodeInterceptors.add(interceptor);
        nodeInterceptors.sort(Comparator.comparingInt(NodeInterceptor::order));
    }

    /**
     * 执行链前置拦截器
     */
    public void beforeChain(String chainCode, ChainContext ctx) {
        for (ChainInterceptor interceptor : chainInterceptors) {
            try {
                interceptor.beforeChain(chainCode, ctx);
            } catch (Exception e) {
                log.warn("链前置拦截器异常 interceptor={}", interceptor.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 执行链后置拦截器
     */
    public void afterChain(String chainCode, ChainContext ctx, List<?> nodeResults) {
        for (ChainInterceptor interceptor : chainInterceptors) {
            try {
                interceptor.afterChain(chainCode, ctx, nodeResults);
            } catch (Exception e) {
                log.warn("链后置拦截器异常 interceptor={}", interceptor.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 执行节点前置拦截器
     */
    public void beforeNode(NodeDefinition node, ChainContext ctx) {
        for (NodeInterceptor interceptor : nodeInterceptors) {
            try {
                interceptor.beforeNode(node, ctx);
            } catch (Exception e) {
                log.warn("节点前置拦截器异常 interceptor={}", interceptor.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 执行节点后置拦截器
     */
    public void afterNode(NodeDefinition node, ChainContext ctx, Object result) {
        for (NodeInterceptor interceptor : nodeInterceptors) {
            try {
                interceptor.afterNode(node, ctx, result);
            } catch (Exception e) {
                log.warn("节点后置拦截器异常 interceptor={}", interceptor.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * 执行链异常拦截器
     */
    public void onChainError(String chainCode, ChainContext ctx, Throwable e) {
        for (ChainInterceptor interceptor : chainInterceptors) {
            try {
                interceptor.onChainError(chainCode, ctx, e);
            } catch (Exception ex) {
                log.warn("链异常拦截器异常", ex);
            }
        }
    }
}
