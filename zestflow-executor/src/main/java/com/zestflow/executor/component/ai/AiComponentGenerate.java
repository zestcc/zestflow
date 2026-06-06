package com.zestflow.executor.component.ai;

import java.lang.annotation.*;

/**
 * AI 辅助组件生成标记注解。
 * <p>
 * 标注此注解的方法将由 AI 辅助生成组件代码骨架，
 * 结合自然语言描述自动生成对应的 @ZestExecute 等组件方法。
 * <p>
 * 使用示例：
 * <pre>{@code
 * @AiComponentGenerate(
 *     description = "查询用户订单列表，支持分页和状态筛选",
 *     inputKeys = {"userId", "pageNum", "pageSize", "status"},
 *     outputKey = "orderList",
 *     category = "BUSINESS"
 * )
 * public List<Order> queryUserOrders(ChainContext ctx) {
 *     // AI 将根据描述生成此方法体
 *     return null;
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AiComponentGenerate {

    /** 自然语言业务描述 */
    String description();

    /** 输入参数 key 列表 */
    String[] inputKeys() default {};

    /** 输出结果 key */
    String outputKey() default "";

    /** 组件分类 */
    Category category() default Category.BUSINESS;

    /** 组件分类枚举 */
    enum Category {
        /** 业务逻辑 */
        BUSINESS,
        /** 数据转换 */
        TRANSFORM,
        /** 外部集成 */
        INTEGRATION,
        /** 数据校验 */
        VALIDATION,
        /** 流程控制 */
        FLOW_CONTROL
    }
}