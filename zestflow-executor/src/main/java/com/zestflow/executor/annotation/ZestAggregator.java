package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为聚合器元件。
 * <p>
 * 用于多分支/多数据源结果汇聚合并，支持 ALL / ANY / FIRST / N_OF_M 等聚合策略。
 * 聚合结果通过 DataBus 向下传递。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestAggregator {

    /** 聚合器唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";

    /** 聚合策略：ALL / ANY / FIRST / N_OF_M */
    String strategy() default "ALL";
}