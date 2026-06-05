package com.zestflow.executor.chain;

import com.zestflow.executor.annotation.ZestChain;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描 {@link ZestChain} 声明 — 对标 {@link com.zestflow.executor.scanner.ComponentScanner}。
 */
@Slf4j
public class ChainDeclarationScanner implements ApplicationContextAware {

    @Getter
    private final List<ChainDeclarationMeta> declarations = new ArrayList<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        scan(applicationContext);
    }

    public void scan(ApplicationContext applicationContext) {
        Map<String, ChainDeclarationMeta> dedup = new LinkedHashMap<>();
        String[] beanNames = applicationContext.getBeanNamesForType(Object.class);
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }
            Class<?> userClass = org.springframework.aop.support.AopUtils.getTargetClass(bean);
            if (!isControllerType(userClass)) {
                continue;
            }
            collectClassLevel(userClass, dedup);
            for (Method method : userClass.getDeclaredMethods()) {
                collectMethodLevel(userClass, method, dedup);
            }
        }
        declarations.clear();
        declarations.addAll(dedup.values());
        log.info("链声明扫描完成 count={}", declarations.size());
    }

    private static boolean isControllerType(Class<?> clazz) {
        return AnnotatedElementUtils.hasAnnotation(clazz, RestController.class)
                || AnnotatedElementUtils.hasAnnotation(clazz, Controller.class)
                || clazz.getSimpleName().endsWith("Controller");
    }

    private static void collectClassLevel(Class<?> clazz, Map<String, ChainDeclarationMeta> dedup) {
        ZestChain ann = AnnotatedElementUtils.findMergedAnnotation(clazz, ZestChain.class);
        if (ann == null) {
            return;
        }
        putMeta(dedup, ann, clazz.getName(), "<class>");
    }

    private static void collectMethodLevel(Class<?> clazz, Method method, Map<String, ChainDeclarationMeta> dedup) {
        ZestChain ann = AnnotatedElementUtils.findMergedAnnotation(method, ZestChain.class);
        if (ann == null) {
            return;
        }
        putMeta(dedup, ann, clazz.getName(), method.getName());
    }

    private static void putMeta(Map<String, ChainDeclarationMeta> dedup, ZestChain ann,
                                 String declaringClass, String declaringMethod) {
        String key = ann.value() != null ? ann.value().trim() : "";
        if (!StringUtils.hasText(key)) {
            log.warn("忽略空 chain_key declaringClass={} method={}", declaringClass, declaringMethod);
            return;
        }
        String name = StringUtils.hasText(ann.name()) ? ann.name().trim() : key;
        ChainDeclarationMeta meta = ChainDeclarationMeta.builder()
                .chainKey(key)
                .name(name)
                .description(ann.description())
                .declaringClass(declaringClass)
                .declaringMethod(declaringMethod)
                .build();
        dedup.put(key, meta);
    }
}
