package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 标记一个方法为消息生产者元件。
 * <p>
 * 用于向消息队列（Kafka / RabbitMQ / RocketMQ）发送消息。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestMqProducer {

    /** 生产者唯一标识，为空时默认取方法名 */
    String value() default "";

    /** 显示名称（可选） */
    String name() default "";

    /** 描述（可选） */
    String description() default "";

    /** 目标 Topic / Queue 名称 */
    String topic() default "";
}