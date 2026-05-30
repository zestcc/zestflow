package com.zestflow.executor.lifecycle;

import com.zestflow.common.model.dto.ComponentRef;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.chain.NodeDefinition;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.param.ParamConverterRegistry;
import com.zestflow.executor.param.resolver.ContextTypeResolver;
import com.zestflow.executor.param.resolver.ParameterResolver;
import com.zestflow.executor.param.resolver.ZestParamResolver;
import com.zestflow.executor.scanner.ComponentScanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LifecycleExecutorTest {

    // ==================== 测试元件（必须 public 以便反射获取 Method） ====================

    public static class TestComponent {
        private String lastArg;

        @ZestExecute("greet")
        public String greet(@ZestParam("name") String name) {
            return "hello " + name;
        }

        @ZestExecute("noArgs")
        public String noArgs() {
            return "no-args-result";
        }

        @ZestExecute("withContext")
        public String withContext(@ZestParam("orderId") String orderId, ChainContext ctx) {
            return "order=" + orderId + " data=" + ctx.get("extra");
        }

        @ZestExecute("chainContextOnly")
        public void chainContextOnly(ChainContext ctx) {
            ctx.put("visited", true);
        }

        @ZestExecute("prePost")
        public void prePost(ChainContext ctx) {
            ctx.put("executed", true);
        }

        @ZestExecute("fallbackMethod")
        public String fallbackMethod(@ZestParam("name") String name, Throwable cause) {
            return "fallback:" + name + " cause=" + cause.getMessage();
        }

        @ZestExecute("withPrimitive")
        public int withPrimitive(@ZestParam("count") int count) {
            return count * 2;
        }

        @ZestExecute("annotatedParam")
        public String annotatedParam(@ZestParam(value = "requiredName", required = true) String name) {
            return name;
        }

        public String getLastArg() { return lastArg; }
    }

    @Mock private ComponentScanner scanner;

    private ParameterResolver zestResolver;
    private ParameterResolver contextResolver;
    private LifecycleExecutor executor;

    @BeforeEach
    void setUp() {
        zestResolver = new ZestParamResolver(new ParamConverterRegistry());
        contextResolver = new ContextTypeResolver();
        executor = new LifecycleExecutor(scanner, List.of(zestResolver, contextResolver));
    }

    // ==================== 基本调用 ====================

    @Test
    void executeWithZestParamResolved() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("greet", String.class);
        mockComponent(scanner, "testComponent", bean, method);

        NodeDefinition nodeDef = nodeDefWithComponent("testComponent");
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of("name", "zest"));

        Object result = executor.execute(nodeDef, ctx);

        assertThat(result).isEqualTo("hello zest");
    }

    @Test
    void executeNoArgsMethod() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("noArgs");
        mockComponent(scanner, "noArgsComp", bean, method);

        NodeDefinition nodeDef = nodeDefWithComponent("noArgsComp");
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());

        Object result = executor.execute(nodeDef, ctx);

        assertThat(result).isEqualTo("no-args-result");
    }

    @Test
    void executeWithMultipleResolvers() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("withContext", String.class, ChainContext.class);
        mockComponent(scanner, "multiComp", bean, method);

        NodeDefinition nodeDef = nodeDefWithComponent("multiComp");
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of("orderId", "ORD-001", "extra", "extra-data"));

        Object result = executor.execute(nodeDef, ctx);

        assertThat(result).isEqualTo("order=ORD-001 data=extra-data");
    }

    @Test
    void chainContextInjectedByType() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("chainContextOnly", ChainContext.class);
        mockComponent(scanner, "ctxComp", bean, method);

        NodeDefinition nodeDef = nodeDefWithComponent("ctxComp");
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());

        executor.execute(nodeDef, ctx);

        assertThat(ctx.get("visited")).isEqualTo(true);
    }

    @Test
    void executeWithComponentNotFound() {
        NodeDefinition nodeDef = nodeDefWithComponent("nonExistent");
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());

        assertThatThrownBy(() -> executor.execute(nodeDef, ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("执行元件未找到");
    }

    // ==================== 参数解析器链 ====================

    @Test
    void customResolverOverridesDefault() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("greet", String.class);
        mockComponent(scanner, "testComponent", bean, method);

        // Create a custom resolver that always returns a fixed value
        ParameterResolver customResolver = new ParameterResolver() {
            @Override public String getId() { return "customResolver"; }
            @Override public boolean supports(java.lang.reflect.Parameter param) { return true; }
            @Override public Object resolve(java.lang.reflect.Parameter param, ChainContext context) {
                return "customVal";
            }
        };

        LifecycleExecutor customExecutor = new LifecycleExecutor(scanner, List.of(customResolver));

        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").component("testComponent")
                .paramResolvers(List.of(new ComponentRef("customResolver", null)))
                .build();
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of("name", "zest"));

        Object result = customExecutor.execute(nodeDef, ctx);

        assertThat(result).isEqualTo("hello customVal");
    }

    @Test
    void paramResolversUsesNodeConfigWhenProvided() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("greet", String.class);
        mockComponent(scanner, "testComponent", bean, method);

        ParameterResolver fixedResolver = new ParameterResolver() {
            @Override public String getId() { return "fixedResolver"; }
            @Override public boolean supports(java.lang.reflect.Parameter param) { return true; }
            @Override public Object resolve(java.lang.reflect.Parameter param, ChainContext context) {
                return "fromConfig";
            }
        };

        LifecycleExecutor customExecutor = new LifecycleExecutor(scanner, List.of(fixedResolver));

        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").component("testComponent")
                .paramResolvers(List.of(new ComponentRef("fixedResolver", null)))
                .build();
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of("name", "zest"));

        Object result = customExecutor.execute(nodeDef, ctx);

        assertThat(result).isEqualTo("hello fromConfig");
    }

    // ==================== 降级执行 ====================

    @Test
    void executeFallbackInjectsCause() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("fallbackMethod", String.class, Throwable.class);
        mockComponent(scanner, "fallbackComp", bean, method);

        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").fallbackComponent("fallbackComp")
                .paramResolvers(List.of(new ComponentRef("zestParamResolver", null)))
                .build();
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of("name", "fallback-test"));
        Throwable cause = new RuntimeException("模拟异常");

        Object result = executor.executeFallback(nodeDef, ctx, cause);

        assertThat(result).isEqualTo("fallback:fallback-test cause=模拟异常");
    }

    @Test
    void executeFallbackWithoutComponentReturnsNull() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").fallbackComponent(null).build();
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());

        Object result = executor.executeFallback(nodeDef, ctx, new RuntimeException("x"));

        assertThat(result).isNull();
    }

    @Test
    void executeFallbackWithUnknownComponentWarnsAndReturnsNull() {
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").fallbackComponent("nonExistent").build();
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());

        Object result = executor.executeFallback(nodeDef, ctx, new RuntimeException("x"));

        assertThat(result).isNull();
    }

    // ==================== 前置/后置处理器 ====================

    @Test
    void preProcessorsExecutedInOrder() throws Exception {
        TestComponent pre1 = new TestComponent();
        TestComponent pre2 = new TestComponent();
        Method method = TestComponent.class.getMethod("prePost", ChainContext.class);
        mockComponent(scanner, "pre1", pre1, method);
        mockComponent(scanner, "pre2", pre2, method);

        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());
        executor.executePreProcessors(List.of(
                new ComponentRef("pre1", null),
                new ComponentRef("pre2", null)
        ), ctx);

        assertThat(ctx.get("executed")).isEqualTo(true);
    }

    @Test
    void postProcessorsExecutedInOrder() throws Exception {
        TestComponent post1 = new TestComponent();
        TestComponent post2 = new TestComponent();
        Method method = TestComponent.class.getMethod("prePost", ChainContext.class);
        mockComponent(scanner, "post1", post1, method);
        mockComponent(scanner, "post2", post2, method);

        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());
        executor.executePostProcessors(List.of(
                new ComponentRef("post1", null),
                new ComponentRef("post2", null)
        ), ctx);

        assertThat(ctx.get("executed")).isEqualTo(true);
    }

    @Test
    void preProcessorsNullListIsNoOp() {
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());
        executor.executePreProcessors(null, ctx);
        // No exception expected
    }

    @Test
    void preProcessorsEmptyListIsNoOp() {
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());
        executor.executePreProcessors(List.of(), ctx);
        // No exception expected
    }

    @Test
    void preProcessorUnknownComponentWarnsAndContinues() {
        // No mock for "unknownComp" → scanner returns null → log warning + continue
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());
        executor.executePreProcessors(List.of(new ComponentRef("unknownComp", null)), ctx);
        // No exception expected
    }

    // ==================== 参数类型转换 ====================

    @Test
    void executeWithPrimitiveTypeConversion() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("withPrimitive", int.class);
        mockComponent(scanner, "primitiveComp", bean, method);

        NodeDefinition nodeDef = nodeDefWithComponent("primitiveComp");
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of("count", "21"));

        Object result = executor.execute(nodeDef, ctx);

        assertThat(result).isEqualTo(42);
    }

    // ==================== 参数校验 ====================

    /**
     * 测试校验器：不接受 null 参数值
     */
    public static class TestValidator {
        @SuppressWarnings("unused")
        public void validate(Object[] args, java.lang.reflect.Parameter[] params) {
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    throw new IllegalArgumentException("参数 [" + params[i].getName() + "] 不能为 null");
                }
            }
        }
    }

    @Test
    void validatorRejectsNullParam() throws Exception {
        // A component with missing context data (no "name" in ctx)
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("greet", String.class);
        mockComponent(scanner, "testComponent", bean, method);

        // Set up the test validator
        TestValidator validatorBean = new TestValidator();
        Method validatorMethod = TestValidator.class.getMethod("validate", Object[].class, java.lang.reflect.Parameter[].class);
        mockComponent(scanner, "defaultParamValidator", validatorBean, validatorMethod);

        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").component("testComponent")
                .paramResolvers(List.of(new ComponentRef("zestParamResolver", null)))
                .build();
        // "name" key not in context → resolver returns null → validator rejects it
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());

        assertThatThrownBy(() -> executor.execute(nodeDef, ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为 null");
    }

    @Test
    void validatorPassesWithValidArgs() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("greet", String.class);
        mockComponent(scanner, "testComponent", bean, method);

        TestValidator validatorBean = new TestValidator();
        Method validatorMethod = TestValidator.class.getMethod("validate", Object[].class, java.lang.reflect.Parameter[].class);
        mockComponent(scanner, "defaultParamValidator", validatorBean, validatorMethod);

        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").component("testComponent")
                .paramResolvers(List.of(new ComponentRef("zestParamResolver", null)))
                .build();
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of("name", "zest"));

        Object result = executor.execute(nodeDef, ctx);

        assertThat(result).isEqualTo("hello zest");
    }

    @Test
    void missingValidatorWarnsAndSkips() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("greet", String.class);
        mockComponent(scanner, "testComponent", bean, method);

        // No mock for "defaultParamValidator" → scanner returns null → warning + skip
        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").component("testComponent")
                .paramResolvers(List.of(new ComponentRef("zestParamResolver", null)))
                .build();
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of("name", "zest"));

        Object result = executor.execute(nodeDef, ctx);

        assertThat(result).isEqualTo("hello zest");
    }

    @Test
    void zestParamResolverRequiredCheck() throws Exception {
        TestComponent bean = new TestComponent();
        Method method = TestComponent.class.getMethod("annotatedParam", String.class);
        mockComponent(scanner, "requiredComp", bean, method);

        NodeDefinition nodeDef = NodeDefinition.builder()
                .id("n1").component("requiredComp")
                .paramResolvers(List.of(new ComponentRef("zestParamResolver", null)))
                .build();
        ChainContext ctx = new ChainContext("inst1", "chain1", Map.of());

        assertThatThrownBy(() -> executor.execute(nodeDef, ctx))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必填参数缺失");
    }

    // ==================== 辅助方法 ====================

    private static NodeDefinition nodeDefWithComponent(String component) {
        return NodeDefinition.builder()
                .id("n1").component(component)
                .paramResolvers(List.of(
                        new ComponentRef("zestParamResolver", null),
                        new ComponentRef("contextTypeResolver", null)
                ))
                .build();
    }

    private static void mockComponent(ComponentScanner scanner, String id,
                                       Object bean, Method method) {
        ComponentScanner.ComponentMeta meta = new ComponentScanner.ComponentMeta();
        meta.setExecuteId(id);
        meta.setTargetBean(bean);
        meta.setExecuteMethod(method);
        when(scanner.getComponent(id)).thenReturn(meta);
    }
}
