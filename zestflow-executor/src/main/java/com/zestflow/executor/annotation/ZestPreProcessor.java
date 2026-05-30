package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为 ZestFlow 前置处理器元件。
 * <p>
 * 在节点主逻辑（@ZestExecute）执行之前运行，
 * 用于参数校验、数据准备、权限检查等预处理操作。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestPreProcessor {

    /**
     * 前置处理器唯一标识。
     * 为空时默认取 "类简单名.方法名"
     */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}
