package com.zestflow.executor.scanner;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
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
 * 组件扫描器：扫描 Spring 容器中所有标注 @ZestExecute 的方法，
 * 每个方法注册为一个独立可编排的执行元件。
 * <p>
 * 注册 key 规则：
 * <ol>
 *   <li>@ZestExecute("createOrder") 的 value 不为空 → 直接使用 "createOrder"</li>
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
     * 将其中的 @ZestExecute 方法注册为独立元件
     */
    public void scan(ApplicationContext applicationContext) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(ZestComponent.class);
        log.info("开始扫描 @ZestExecute 执行元件，共发现 {} 个组件类", beans.size());

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> rawClass = AopProxyUtils.ultimateTargetClass(bean);
            Class<?> targetClass = rawClass != null ? rawClass : bean.getClass();

            ZestComponent compAnn = AnnotationUtils.findAnnotation(targetClass, ZestComponent.class);
            String groupName = (compAnn != null) ? compAnn.value() : "";

            // 扫描 @ZestExecute 方法（使用 Spring 缓存 Method[]，避免 getMethods() 重复分配）
            ReflectionUtils.doWithMethods(targetClass,
                    method -> {
                        ZestExecute execAnn = method.getAnnotation(ZestExecute.class);
                        if (execAnn == null) {
                            return;
                        }

                        String executeId = resolveExecuteId(execAnn, method);
                        if (registry.containsKey(executeId)) {
                            log.warn("执行元件 ID 重复: {}，后扫描的将覆盖之前的", executeId);
                        }

                        ComponentMeta meta = buildComponentMeta(executeId, groupName, bean, targetClass, method, execAnn);
                        registry.put(executeId, meta);
                        log.debug("注册执行元件 executeId={} class={}.{}()",
                                executeId, targetClass.getSimpleName(), method.getName());
                    },
                    method -> !method.getDeclaringClass().equals(Object.class)
            );
        }

        log.info("@ZestExecute 执行元件扫描完成，共注册 {} 个元件", registry.size());
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

    private String resolveExecuteId(ZestExecute annotation, Method method) {
        String id = annotation.value();
        if (id != null && !id.isEmpty()) {
            return id;
        }
        return method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    private ComponentMeta buildComponentMeta(String executeId, String groupName,
                                              Object bean, Class<?> targetClass,
                                              Method method, ZestExecute annotation) {
        ComponentMeta meta = new ComponentMeta();
        meta.setExecuteId(executeId);
        meta.setGroupName(groupName);
        meta.setName(annotation.name());
        meta.setDescription(annotation.description());
        meta.setTargetBean(bean);
        meta.setTargetClass(targetClass);
        meta.setExecuteMethod(method);
        meta.setTimeout(annotation.timeout());
        meta.setAsync(annotation.async());

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
