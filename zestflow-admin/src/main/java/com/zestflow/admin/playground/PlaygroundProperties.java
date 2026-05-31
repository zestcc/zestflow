package com.zestflow.admin.playground;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 试验场配置 — {@code zestflow.playground.enabled=true} 时加载
 */
@Data
@Component
@ConditionalOnProperty(prefix = "zestflow.playground", name = "enabled", havingValue = "true", matchIfMissing = false)
@ConfigurationProperties("zestflow.playground")
public class PlaygroundProperties {

    /** 是否启用（同时也是开关） */
    private boolean enabled = false;

    /** 演示用的链所属 appCode */
    private String appCode = "default";

    /** 每 IP 每分钟允许的最大请求数 */
    private int rateLimit = 30;

    /** 演示场景列表 */
    private List<SceneConfig> scenes = new ArrayList<>();

    @Data
    public static class SceneConfig {
        /** 场景标识，如 hello */
        private String id;
        /** 场景名称 */
        private String name;
        /** 对应链编码 */
        private String chainCode;
        /** 场景描述 */
        private String description;
        /** 默认参数字典（key: 参数名, value: 示例值），仅用于场景介绍 */
        private java.util.Map<String, String> defaultParams;
    }
}
