package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * {@link ZestTag} 容器注解，用于一次性指定多个标签。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ZestTags({
 *     @ZestTag(name="在线订单", value="online"),
 *     @ZestTag(name="线下订单", value="offline")
 * })
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestTags {

    ZestTag[] value();
}
