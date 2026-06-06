package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为消息消费者元件。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestMqConsumer {

    /** 消费者唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";

    /** 来源 Topic / Queue 名称 */
    String topic() default "";
}
