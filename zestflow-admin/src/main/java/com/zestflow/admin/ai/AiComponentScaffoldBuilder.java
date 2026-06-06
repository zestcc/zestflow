package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.dto.AiComponentScaffoldRequest;
import com.zestflow.common.model.ComponentType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 元件 Java 脚手架生成（无 Executor 依赖，逻辑对齐 AiComponentCodeGenerator）
 */
@Component
public class AiComponentScaffoldBuilder {

    public String generateJavaClass(ScaffoldDefinition definition) {
        StringBuilder sb = new StringBuilder();
        sb.append("package com.zestflow.component.generated;\n\n");
        sb.append("import com.zestflow.executor.annotation.*;\n");
        sb.append("import com.zestflow.executor.context.ChainContext;\n");
        sb.append("import lombok.extern.slf4j.Slf4j;\n");
        sb.append("import org.springframework.stereotype.Component;\n\n");
        String className = toClassName(definition.componentName());
        sb.append("@Slf4j\n");
        sb.append("@Component\n");
        sb.append("@ZestComponent(\"").append(definition.groupName()).append("\")\n");
        sb.append("public class ").append(className).append(" {\n\n");
        sb.append(generateComponentMethod(definition));
        sb.append("}\n");
        return sb.toString();
    }

    public String generateComponentMethod(ScaffoldDefinition definition) {
        StringBuilder sb = new StringBuilder();
        String methodName = toMethodName(definition.componentName());
        String annotation = getAnnotationForType(definition.componentType());

        sb.append("    /**\n");
        sb.append("     * ").append(definition.description()).append("\n");
        sb.append("     */\n");
        sb.append("    ").append(annotation).append("(\"").append(definition.componentId()).append("\")\n");
        sb.append("    public Object ").append(methodName).append("(ChainContext ctx) {\n");
        sb.append("        log.debug(\"").append(methodName).append(" 开始执行\");\n");
        sb.append("        \n");
        sb.append("        // TODO: 根据业务需求实现具体逻辑\n");
        sb.append("        // ").append(definition.description()).append("\n");
        sb.append("        \n");

        if (definition.inputParams() != null && !definition.inputParams().isEmpty()) {
            sb.append("        // 提取输入参数\n");
            for (ScaffoldParam param : definition.inputParams()) {
                sb.append("        Object ").append(param.name())
                        .append(" = ctx.get(\"").append(param.name()).append("\");\n");
            }
            sb.append("        \n");
        }

        sb.append("        // 执行业务逻辑\n");
        sb.append("        Object result = null;\n");
        sb.append("        \n");

        if (definition.outputParams() != null && !definition.outputParams().isEmpty()) {
            sb.append("        // 将结果写入上下文\n");
            for (ScaffoldParam param : definition.outputParams()) {
                sb.append("        ctx.put(\"").append(param.name()).append("\", result);\n");
            }
        }

        sb.append("        \n");
        sb.append("        log.debug(\"").append(methodName).append(" 执行完成\");\n");
        sb.append("        return result;\n");
        sb.append("    }\n");
        return sb.toString();
    }

    public ScaffoldDefinition fromRequest(AiComponentScaffoldRequest request) {
        ComponentType type = parseComponentType(request.getComponentType());
        String componentName = StringUtils.hasText(request.getComponentId())
                ? request.getComponentId() : "generated";
        List<ScaffoldParam> inputs = mapParams(request.getInputParams());
        List<ScaffoldParam> outputs = mapParams(request.getOutputParams());
        return new ScaffoldDefinition(
                request.getComponentId(),
                componentName,
                type,
                nullToDefault(request.getGroupName(), "generated"),
                nullToDefault(request.getDescription(), "待实现业务逻辑"),
                inputs,
                outputs
        );
    }

    public List<String> buildChecklist(String componentId) {
        List<String> list = new ArrayList<>();
        list.add("复制到 Executor 工程对应包路径");
        list.add("补全 TODO 业务逻辑");
        list.add("mvn package 并部署");
        list.add("Admin 发布/reload 后在元件列表确认 " + componentId + " 出现");
        return list;
    }

    private List<ScaffoldParam> mapParams(List<AiComponentScaffoldRequest.ParamItem> items) {
        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(p -> new ScaffoldParam(p.getName(), p.getType(), p.isRequired()))
                .toList();
    }

    private ComponentType parseComponentType(String type) {
        if (!StringUtils.hasText(type)) {
            return ComponentType.EXECUTOR;
        }
        try {
            return ComponentType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ComponentType.EXECUTOR;
        }
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
        if (!StringUtils.hasText(name)) {
            return "GeneratedComponent";
        }
        return name.substring(0, 1).toUpperCase() + name.substring(1) + "Component";
    }

    private String toMethodName(String name) {
        if (!StringUtils.hasText(name)) {
            return "execute";
        }
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    private static String nullToDefault(String value, String defaultVal) {
        return StringUtils.hasText(value) ? value : defaultVal;
    }

    public record ScaffoldParam(String name, String type, boolean required) {}

    public record ScaffoldDefinition(
            String componentId,
            String componentName,
            ComponentType componentType,
            String groupName,
            String description,
            List<ScaffoldParam> inputParams,
            List<ScaffoldParam> outputParams
    ) {}
}
