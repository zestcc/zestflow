package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为数据拆分器元件。
 * <p>
 * 将大数据集拆分为多个子集，输出为拆分后的集合，
 * 常用于并行处理场景的前置拆分。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestSplitter {

    /** 拆分器唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";
}