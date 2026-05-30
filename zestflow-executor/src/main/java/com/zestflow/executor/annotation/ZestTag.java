package com.zestflow.executor.annotation;

import com.zestflow.common.model.TagValueType;

import java.lang.annotation.*;

/**
 * 标记一个标签定义，用于方法上的路由分支标识。
 * <p>
 * 可单独使用多个 {@code @ZestTag}，也可用 {@link ZestTags} 容器一次性指定。
 * 扫描时会合并为并集。
 * <p>
 * 设计器中连线选择标签时，显示 name，路由匹配 value。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ZestComponent("order")
 * public class OrderHandler {
 *     @ZestSelector("routeByChannel")
 *     @ZestTag(name="在线订单", value="online")
 *     @ZestTag(name="线下订单", value="offline")
 *     public String route(ChainContext ctx) { ... }
 * }
 * }</pre>
 */
@Repeatable(ZestTags.class)
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestTag {

    /** 显示名称 */
    String name();

    /** 路由匹配值 */
    String value();

    /** 值类型，默认 STRING */
    TagValueType type() default TagValueType.STRING;
}
