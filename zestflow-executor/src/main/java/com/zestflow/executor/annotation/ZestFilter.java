package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为数据过滤器元件。
 * <p>
 * 用于数据筛选、去重、条件过滤，输入为集合或单条数据，
 * 输出为过滤后的结果。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestFilter {

    /** 过滤器唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}