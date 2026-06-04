package com.zestflow.executor.annotation;

import java.lang.annotation.*;

/**
 * 声明元件返回值写入 ChainContext DataBus 的 key。
 * <p>
 * 对 {@link String}、数字、布尔等简单类型，引擎默认不会自动发布返回值；
 * 标注此注解后，执行完成会将返回值 {@code context.put(key, result)}，供下游 {@link ZestParam} 或参数名注入消费。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ZestOutput {

    /** DataBus 中的 key */
    String value();
}
