package com.zestflow.executor.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.*;

/**
 * 标记一个 Spring Bean 为 ZestFlow 组件容器（类似 @Controller）。
 * <p>
 * 组件内通过 @ZestExecute 标注可编排的元件方法，
 * value() 作为可选的逻辑分组名称，不参与执行器注册。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface ZestComponent {

    /**
     * 逻辑分组名称（可选），用于在 Admin 页面归类展示
     */
    String value() default "";
}
