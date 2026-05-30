package com.zestflow.executor.scanner;

import com.zestflow.common.model.ComponentType;
import com.zestflow.executor.annotation.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 组件扫描器：扫描 Spring 容器中所有标注 @ZestComponent 的类，
 * 将其中的 @ZestExecute / @ZestPredicate / @ZestSelector / @ZestLoader / @ZestParser
 * / @ZestPreProcessor / @ZestPostProcessor / @ZestParamBinder / @ZestParamValidator 方法注册为独立可编排的执行元件。
 * <p>
 * 注册 key 规则：
 * <ol>
 *   <li>注解 value 不为空 → 直接使用该值</li>
 *   <li>value 为空 → 默认 "类简单名.方法名"</li>
 * </ol>
 */
@Slf4j
public class ComponentScanner implements ApplicationContextAware {

    /**
     * 元件注册表：executeId → ComponentMeta
     */
    @Getter
    private final Map<String, ComponentMeta> registry = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        scan(applicationContext);
    }

    /**
     * 扫描 ApplicationContext 中所有带 @ZestComponent 的类，
     * 将其中的注解方法注册为独立元件
     */
    public void scan(ApplicationContext applicationContext) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(ZestComponent.class);
        log.info("开始扫描执行元件，共发现 {} 个组件类", beans.size());

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> rawClass = AopProxyUtils.ultimateTargetClass(bean);
            Class<?> targetClass = rawClass != null ? rawClass : bean.getClass();

            ZestComponent compAnn = AnnotationUtils.findAnnotation(targetClass, ZestComponent.class);
            String groupName = (compAnn != null) ? compAnn.value() : "";

            // 扫描类中所有带元件注解的方法
            ReflectionUtils.doWithMethods(targetClass,
                    method -> {
                        ComponentType type = resolveComponentType(method);
                        if (type == null) return;

                        String annotationValue = resolveAnnotationValue(method, type);
                        String executeId = resolveExecuteId(annotationValue, method);
                        if (registry.containsKey(executeId)) {
                            log.warn("执行元件 ID 重复: {}，后扫描的将覆盖之前的", executeId);
                        }

                        ComponentMeta meta = buildComponentMeta(executeId, groupName, bean, targetClass, method, type);
                        registry.put(executeId, meta);
                        log.debug("注册执行元件 type={} executeId={} class={}.{}()",
                                type, executeId, targetClass.getSimpleName(), method.getName());
                    },
                    method -> !method.getDeclaringClass().equals(Object.class)
            );
        }

        log.info("执行元件扫描完成，共注册 {} 个元件", registry.size());
    }

    /**
     * 根据 executeId 查找元件元数据
     */
    public ComponentMeta getComponent(String executeId) {
        ComponentMeta meta = registry.get(executeId);
        if (meta == null) {
            log.warn("执行元件未找到 executeId={}，已注册的元件: {}", executeId, registry.keySet());
        }
        return meta;
    }

    /**
     * 获取所有已注册的元件 ID
     */
    public Set<String> getComponentIds() {
        return registry.keySet();
    }

    /**
     * 获取元件数量
     */
    public int componentCount() {
        return registry.size();
    }

    // ==================== 私有方法 ====================

    /**
     * 解析方法上的元件注解类型，无对应注解时返回 null
     */
    private ComponentType resolveComponentType(Method method) {
        if (method.getAnnotation(ZestExecute.class) != null) return ComponentType.EXECUTOR;
        if (method.getAnnotation(ZestPredicate.class) != null) return ComponentType.PREDICATE;
        if (method.getAnnotation(ZestSelector.class) != null) return ComponentType.SELECTOR;
        if (method.getAnnotation(ZestLoader.class) != null) return ComponentType.LOADER;
        if (method.getAnnotation(ZestParser.class) != null) return ComponentType.PARSER;
        if (method.getAnnotation(ZestPreProcessor.class) != null) return ComponentType.PRE_PROCESSOR;
        if (method.getAnnotation(ZestPostProcessor.class) != null) return ComponentType.POST_PROCESSOR;
        if (method.getAnnotation(ZestParamBinder.class) != null) return ComponentType.PARAM_BINDER;
        if (method.getAnnotation(ZestParamValidator.class) != null) return ComponentType.PARAM_VALIDATOR;
        return null;
    }

    /**
     * 从方法和类型中提取注解的 value 值
     */
    private String resolveAnnotationValue(Method method, ComponentType type) {
        return switch (type) {
            case EXECUTOR -> {
                ZestExecute a = method.getAnnotation(ZestExecute.class);
                yield a != null ? a.value() : "";
            }
            case PREDICATE -> {
                ZestPredicate a = method.getAnnotation(ZestPredicate.class);
                yield a != null ? a.value() : "";
            }
            case SELECTOR -> {
                ZestSelector a = method.getAnnotation(ZestSelector.class);
                yield a != null ? a.value() : "";
            }
            case LOADER -> {
                ZestLoader a = method.getAnnotation(ZestLoader.class);
                yield a != null ? a.value() : "";
            }
            case PARSER -> {
                ZestParser a = method.getAnnotation(ZestParser.class);
                yield a != null ? a.value() : "";
            }
            case PRE_PROCESSOR -> {
                ZestPreProcessor a = method.getAnnotation(ZestPreProcessor.class);
                yield a != null ? a.value() : "";
            }
            case POST_PROCESSOR -> {
                ZestPostProcessor a = method.getAnnotation(ZestPostProcessor.class);
                yield a != null ? a.value() : "";
            }
            case PARAM_BINDER -> {
                ZestParamBinder a = method.getAnnotation(ZestParamBinder.class);
                yield a != null ? a.value() : "";
            }
            case PARAM_VALIDATOR -> {
                ZestParamValidator a = method.getAnnotation(ZestParamValidator.class);
                yield a != null ? a.value() : "";
            }
        };
    }

    private String resolveExecuteId(String annotationValue, Method method) {
        if (annotationValue != null && !annotationValue.isEmpty()) {
            return annotationValue;
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private ComponentMeta buildComponentMeta(String executeId, String groupName,
                                              Object bean, Class<?> targetClass,
                                              Method method, ComponentType type) {
        ComponentMeta meta = new ComponentMeta();
        meta.setExecuteId(executeId);
        meta.setGroupName(groupName);
        meta.setComponentType(type);
        meta.setTargetBean(bean);
        meta.setTargetClass(targetClass);
        meta.setExecuteMethod(method);

        // 按类型提取名称和描述
        switch (type) {
            case EXECUTOR -> {
                ZestExecute a = method.getAnnotation(ZestExecute.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                    meta.setTimeout(a.timeout());
                    meta.setAsync(a.async());
                }
            }
            case PREDICATE -> {
                ZestPredicate a = method.getAnnotation(ZestPredicate.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                }
            }
            case SELECTOR -> {
                ZestSelector a = method.getAnnotation(ZestSelector.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                }
            }
            case LOADER -> {
                ZestLoader a = method.getAnnotation(ZestLoader.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                }
            }
            case PARSER -> {
                ZestParser a = method.getAnnotation(ZestParser.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                }
            }
            case PRE_PROCESSOR -> {
                ZestPreProcessor a = method.getAnnotation(ZestPreProcessor.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                }
            }
            case POST_PROCESSOR -> {
                ZestPostProcessor a = method.getAnnotation(ZestPostProcessor.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                }
            }
            case PARAM_BINDER -> {
                ZestParamBinder a = method.getAnnotation(ZestParamBinder.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                }
            }
            case PARAM_VALIDATOR -> {
                ZestParamValidator a = method.getAnnotation(ZestParamValidator.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                }
            }
        }

        // 扫描 @ZestParam 字段
        scanZestParamFields(targetClass, meta);

        return meta;
    }

    private void scanZestParamFields(Class<?> targetClass, ComponentMeta meta) {
        for (Field field : targetClass.getDeclaredFields()) {
            ZestParam param = field.getAnnotation(ZestParam.class);
            if (param != null) {
                field.setAccessible(true);
                meta.addParamField(field, param);
            }
        }
    }

    // ==================== 内部类 ====================

    /**
     * 执行元件元数据
     */
    @lombok.Data
    public static class ComponentMeta {
        /** 执行元件 ID，链定义中 node.component 引用此值 */
        private String executeId;
        /** 所属分组名称（@ZestComponent value） */
        private String groupName;
        /** 元件类型 */
        private ComponentType componentType = ComponentType.EXECUTOR;
        /** 显示名称 */
        private String name;
        /** 描述 */
        private String description;
        /** 目标 Bean 实例 */
        private Object targetBean;
        /** 目标类（已解代理） */
        private Class<?> targetClass;
        /** @ZestExecute 方法引用 */
        private Method executeMethod;
        /** 超时时间 */
        private long timeout = -1;
        /** 是否异步 */
        private boolean async;
        /** @ZestParam 字段列表 */
        private List<ParamField> paramFields = new ArrayList<>();

        public void addParamField(Field field, ZestParam annotation) {
            paramFields.add(new ParamField(field, annotation));
        }
    }

    /**
     * 参数注入字段信息
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ParamField {
        private Field field;
        private ZestParam annotation;
    }
}
