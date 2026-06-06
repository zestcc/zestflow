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

import java.util.stream.Collectors;
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
 *   <li>value 为空 → 默认使用方法名</li>
 *   <li>name 为空 → 默认使用 value（executeId）</li>
 * </ol>
 */
@Slf4j
public class ComponentScanner implements ApplicationContextAware {

    /**
     * 元件注册表：executeId → ComponentMeta
     */
    @Getter
    private final Map<String, ComponentMeta> registry = new ConcurrentHashMap<>();

    /** ApplicationContext 引用，用于运行时刷新 */
    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        scan(applicationContext);
    }

    /**
     * 扫描 ApplicationContext 中所有带 @ZestComponent 的类，
     * 将其中的注解方法注册为独立元件
     */
    public void scan(ApplicationContext applicationContext) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(ZestComponent.class);
        log.info("开始扫描执行元件，共发现 {} 个组件类", beans.size());

        Map<String, ComponentMeta> pending = new LinkedHashMap<>();
        Map<String, List<String>> idToPaths = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            Class<?> rawClass = AopProxyUtils.ultimateTargetClass(bean);
            Class<?> targetClass = rawClass != null ? rawClass : bean.getClass();

            ZestComponent compAnn = AnnotationUtils.findAnnotation(targetClass, ZestComponent.class);
            String groupName = (compAnn != null) ? compAnn.value() : "";

            ReflectionUtils.doWithMethods(targetClass,
                    method -> {
                        ComponentType type = resolveComponentType(method);
                        if (type == null) return;

                        String annotationValue = resolveAnnotationValue(method, type);
                        String executeId = resolveExecuteId(annotationValue, method);
                        String methodPath = formatMethodPath(targetClass, method);

                        idToPaths.computeIfAbsent(executeId, k -> new ArrayList<>()).add(methodPath);
                        pending.put(executeId, buildComponentMeta(executeId, groupName, bean, targetClass, method, type));
                        log.debug("扫描执行元件 type={} executeId={} path={}",
                                type, executeId, methodPath);
                    },
                    method -> !method.getDeclaringClass().equals(Object.class)
            );
        }

        assertNoConflicts(idToPaths);
        registry.clear();
        registry.putAll(pending);

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

    /**
     * 运行时刷新元件注册表（重新扫描所有 @ZestComponent Bean）
     * 适用于热加载场景，不会中断正在执行的请求
     */
    public synchronized int refresh() {
        if (applicationContext == null) {
            log.warn("ApplicationContext 未初始化，无法刷新");
            return registry.size();
        }
        int oldSize = registry.size();
        scan(applicationContext);
        log.info("元件注册表刷新完成 old={} new={}", oldSize, registry.size());
        return registry.size();
    }

    /**
     * 动态注册单个元件（不依赖 Spring Bean，用于热加载）
     *
     * @return true 表示新增，false 表示覆盖已有
     */
    public boolean register(String executeId, ComponentMeta meta) {
        if (executeId == null || executeId.isEmpty()) {
            throw new IllegalArgumentException("executeId 不能为空");
        }
        if (registry.containsKey(executeId)) {
            ComponentMeta existing = registry.get(executeId);
            List<String> paths = new ArrayList<>(2);
            paths.add(describeComponentPath(existing));
            paths.add(describeComponentPath(meta));
            throw new ComponentIdConflictException(Map.of(executeId, paths));
        }
        registry.put(executeId, meta);
        log.info("动态注册元件 executeId={} type={}", executeId, meta.getComponentType());
        return true;
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
        if (method.getAnnotation(ZestTransformer.class) != null) return ComponentType.TRANSFORMER;
        if (method.getAnnotation(ZestFilter.class) != null) return ComponentType.FILTER;
        if (method.getAnnotation(ZestAggregator.class) != null) return ComponentType.AGGREGATOR;
        if (method.getAnnotation(ZestSplitter.class) != null) return ComponentType.SPLITTER;
        if (method.getAnnotation(ZestHttpClient.class) != null) return ComponentType.HTTP_CLIENT;
        if (method.getAnnotation(ZestMqProducer.class) != null) return ComponentType.MQ_PRODUCER;
        if (method.getAnnotation(ZestMqConsumer.class) != null) return ComponentType.MQ_CONSUMER;
        if (method.getAnnotation(ZestCacheReader.class) != null) return ComponentType.CACHE_READER;
        if (method.getAnnotation(ZestCacheWriter.class) != null) return ComponentType.CACHE_WRITER;
        if (method.getAnnotation(ZestLogger.class) != null) return ComponentType.LOGGER;
        if (method.getAnnotation(ZestDelay.class) != null) return ComponentType.DELAY;
        if (method.getAnnotation(ZestPreProcessor.class) != null) return ComponentType.PRE_PROCESSOR;
        if (method.getAnnotation(ZestPostProcessor.class) != null) return ComponentType.POST_PROCESSOR;
        if (method.getAnnotation(ZestParamBinder.class) != null) return ComponentType.PARAM_BINDER;
        if (method.getAnnotation(ZestParamValidator.class) != null) return ComponentType.PARAM_VALIDATOR;
        if (method.getAnnotation(ZestErrorHandler.class) != null) return ComponentType.ERROR_HANDLER;
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
            case TRANSFORMER -> {
                ZestTransformer a = method.getAnnotation(ZestTransformer.class);
                yield a != null ? a.value() : "";
            }
            case FILTER -> {
                ZestFilter a = method.getAnnotation(ZestFilter.class);
                yield a != null ? a.value() : "";
            }
            case AGGREGATOR -> {
                ZestAggregator a = method.getAnnotation(ZestAggregator.class);
                yield a != null ? a.value() : "";
            }
            case SPLITTER -> {
                ZestSplitter a = method.getAnnotation(ZestSplitter.class);
                yield a != null ? a.value() : "";
            }
            case HTTP_CLIENT -> {
                ZestHttpClient a = method.getAnnotation(ZestHttpClient.class);
                yield a != null ? a.value() : "";
            }
            case MQ_PRODUCER -> {
                ZestMqProducer a = method.getAnnotation(ZestMqProducer.class);
                yield a != null ? a.value() : "";
            }
            case MQ_CONSUMER -> {
                ZestMqConsumer a = method.getAnnotation(ZestMqConsumer.class);
                yield a != null ? a.value() : "";
            }
            case CACHE_READER -> {
                ZestCacheReader a = method.getAnnotation(ZestCacheReader.class);
                yield a != null ? a.value() : "";
            }
            case CACHE_WRITER -> {
                ZestCacheWriter a = method.getAnnotation(ZestCacheWriter.class);
                yield a != null ? a.value() : "";
            }
            case LOGGER -> {
                ZestLogger a = method.getAnnotation(ZestLogger.class);
                yield a != null ? a.value() : "";
            }
            case DELAY -> {
                ZestDelay a = method.getAnnotation(ZestDelay.class);
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
            case ERROR_HANDLER -> {
                ZestErrorHandler a = method.getAnnotation(ZestErrorHandler.class);
                yield a != null ? a.value() : "";
            }
            case APPROVAL, NOTIFICATION,
                 FORK, JOIN, TRY_CATCH, WHILE -> "";
        };
    }

    private String resolveExecuteId(String annotationValue, Method method) {
        if (annotationValue != null && !annotationValue.isEmpty()) {
            return annotationValue;
        }
        return method.getName();
    }

    private String formatMethodPath(Class<?> targetClass, Method method) {
        return targetClass.getName() + "#" + method.getName() + "()";
    }

    private String describeComponentPath(ComponentMeta meta) {
        if (meta.getTargetClass() != null && meta.getExecuteMethod() != null) {
            return formatMethodPath(meta.getTargetClass(), meta.getExecuteMethod());
        }
        return meta.getExecuteId();
    }

    private void assertNoConflicts(Map<String, List<String>> idToPaths) {
        Map<String, List<String>> conflicts = idToPaths.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
        if (!conflicts.isEmpty()) {
            throw new ComponentIdConflictException(conflicts);
        }
    }

    private void applyDisplayNameDefault(ComponentMeta meta) {
        if (meta.getName() == null || meta.getName().isEmpty()) {
            meta.setName(meta.getExecuteId());
        }
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
            case ERROR_HANDLER -> {
                ZestErrorHandler a = method.getAnnotation(ZestErrorHandler.class);
                if (a != null) {
                    meta.setName(a.name());
                    meta.setDescription(a.description());
                }
            }
        }

        // 扫描 @ZestOutput（简单类型返回值写入 DataBus）
        ZestOutput output = method.getAnnotation(ZestOutput.class);
        if (output != null && !output.value().isEmpty()) {
            meta.setOutputKey(output.value());
        }

        // 扫描 @ZestTag 标签定义（所有类型元件都可标注）
        scanZestTags(method, meta);

        // 扫描 @ZestParam 字段
        scanZestParamFields(targetClass, meta);

        applyDisplayNameDefault(meta);
        return meta;
    }

    /**
     * 扫描方法上的 @ZestTag 注解（支持单个、多个和 @ZestTags 容器），
     * 去重合并到元数据
     */
    private void scanZestTags(Method method, ComponentMeta meta) {
        ZestTag[] tags = method.getAnnotationsByType(ZestTag.class);
        if (tags == null || tags.length == 0) return;

        Set<String> seen = new HashSet<>();
        for (ZestTag tag : tags) {
            String key = tag.name() + "|" + tag.value();
            if (!seen.add(key)) continue;
            meta.getTagDefs().add(new TagDef(tag.name(), tag.value()));
        }
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

        /** @ZestTag 标签定义列表 */
        private List<TagDef> tagDefs = new ArrayList<>();

        /** @ZestOutput 写入 DataBus 的 key（简单类型返回值） */
        private String outputKey;

        public void addParamField(Field field, ZestParam annotation) {
            paramFields.add(new ParamField(field, annotation));
        }
    }

    /**
     * 标签定义（name + value）
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class TagDef {
        /** 显示名称 */
        private String name;
        /** 路由匹配值 */
        private String value;
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
