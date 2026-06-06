package com.zestflow.executor.component.ai;

import com.zestflow.common.model.ComponentType;
import com.zestflow.executor.annotation.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 组件代码生成器。
 * <p>
 * 根据业务描述自动生成符合 ZestFlow 规范的组件代码骨架。
 * 生产环境可对接 LLM API（如 OpenAI / 本地模型）进行智能代码生成。
 */
@Slf4j
public class AiComponentCodeGenerator {

    /**
     * 根据组件定义生成完整的 Java 类代码。
     *
     * @param definition AI 组件定义
     * @return 完整的 Java 类代码字符串
     */
    public String generateJavaClass(AiComponentDefinition definition) {
        StringBuilder sb = new StringBuilder();

        // 包声明
        sb.append("package com.zestflow.component.generated;\n\n");

        // 导入
        sb.append("import com.zestflow.executor.annotation.*;\n");
        sb.append("import com.zestflow.executor.context.ChainContext;\n");
        sb.append("import lombok.extern.slf4j.Slf4j;\n");
        sb.append("import org.springframework.stereotype.Component;\n\n");

        // 类声明
        String className = toClassName(definition.getComponentName());
        sb.append("@Slf4j\n");
        sb.append("@Component\n");
        sb.append("@ZestComponent(\"").append(definition.getGroupName()).append("\")\n");
        sb.append("public class ").append(className).append(" {\n\n");

        // 生成组件方法
        sb.append(generateComponentMethod(definition));

        sb.append("}\n");

        return sb.toString();
    }

    /**
     * 生成组件方法代码骨架。
     */
    public String generateComponentMethod(AiComponentDefinition definition) {
        StringBuilder sb = new StringBuilder();
        String methodName = toMethodName(definition.getComponentName());
        String annotation = getAnnotationForType(definition.getComponentType());

        sb.append("    /**\n");
        sb.append("     * ").append(definition.getDescription()).append("\n");
        sb.append("     */\n");
        sb.append("    ").append(annotation).append("(\"").append(definition.getComponentId()).append("\")\n");
        sb.append("    public Object ").append(methodName).append("(ChainContext ctx) {\n");

        // 方法体骨架
        sb.append("        log.debug(\"").append(methodName).append(" 开始执行\");\n");
        sb.append("        \n");
        sb.append("        // TODO: 根据业务需求实现具体逻辑\n");
        sb.append("        // ").append(definition.getDescription()).append("\n");
        sb.append("        \n");

        // 输入参数提取
        if (definition.getInputParams() != null && !definition.getInputParams().isEmpty()) {
            sb.append("        // 提取输入参数\n");
            for (AiComponentDefinition.ParamSchema param : definition.getInputParams()) {
                sb.append("        Object ").append(param.getName())
                        .append(" = ctx.get(\"").append(param.getName()).append("\");\n");
            }
            sb.append("        \n");
        }

        sb.append("        // 执行业务逻辑\n");
        sb.append("        Object result = null;\n");
        sb.append("        \n");

        // 输出结果
        if (definition.getOutputParams() != null && !definition.getOutputParams().isEmpty()) {
            sb.append("        // 将结果写入上下文\n");
            for (AiComponentDefinition.ParamSchema param : definition.getOutputParams()) {
                sb.append("        ctx.put(\"").append(param.getName()).append("\", result);\n");
            }
        }

        sb.append("        \n");
        sb.append("        log.debug(\"").append(methodName).append(" 执行完成\");\n");
        sb.append("        return result;\n");
        sb.append("    }\n");

        return sb.toString();
    }

    private String getAnnotationForType(ComponentType type) {
        return switch (type) {
            case EXECUTOR -> "@ZestExecute";
            case PREDICATE -> "@ZestPredicate";
            case SELECTOR -> "@ZestSelector";
            case LOADER -> "@ZestLoader";
            case PARSER -> "@ZestParser";
            case TRANSFORMER -> "@ZestTransformer";
            case FILTER -> "@ZestFilter";
            case AGGREGATOR -> "@ZestAggregator";
            case SPLITTER -> "@ZestSplitter";
            case HTTP_CLIENT -> "@ZestHttpClient";
            case MQ_PRODUCER -> "@ZestMqProducer";
            case CACHE_READER -> "@ZestCacheReader";
            case CACHE_WRITER -> "@ZestCacheWriter";
            case LOGGER -> "@ZestLogger";
            case DELAY -> "@ZestDelay";
            default -> "@ZestExecute";
        };
    }

    private String toClassName(String name) {
        if (name == null || name.isEmpty()) return "GeneratedComponent";
        return name.substring(0, 1).toUpperCase() + name.substring(1) + "Component";
    }

    private String toMethodName(String name) {
        if (name == null || name.isEmpty()) return "execute";
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }
}