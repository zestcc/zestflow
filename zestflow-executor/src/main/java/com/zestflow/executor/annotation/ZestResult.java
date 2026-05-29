package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 结果注入：标记方法参数注入当前节点的执行结果。
 * <p>
 * 用于接收前一个执行元件的返回值，通过链上下文传递。
 * <pre>
 * public void afterProcess(ChainContext ctx, @ZestResult Object result) {
 *     // 使用 result 继续处理
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestResult {
}
