package com.zestflow.executor.scanner;

import com.zestflow.common.model.ComponentType;
import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestLoader;
import com.zestflow.executor.annotation.ZestParser;
import com.zestflow.executor.annotation.ZestPredicate;
import com.zestflow.executor.annotation.ZestSelector;
import com.zestflow.executor.context.ChainContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ComponentScannerTest {

    private ComponentScanner scanner;

    @BeforeEach
    void setUp() {
        scanner = new ComponentScanner();
    }

    @Test
    void scanWithExplicitExecuteId() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(ZestComponent.class))
                .thenReturn(Map.of("testHandler", new TestHandler()));

        scanner.scan(ctx);

        assertThat(scanner.componentCount()).isEqualTo(2);
        assertThat(scanner.getComponentIds()).contains("doSomething", "doAnother");
    }

    @Test
    void scanWithDefaultExecuteId() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(ZestComponent.class))
                .thenReturn(Map.of("defaultHandler", new DefaultIdHandler()));

        scanner.scan(ctx);

        assertThat(scanner.componentCount()).isEqualTo(1);
        // 默认 ID = 类简单名.方法名
        assertThat(scanner.getComponentIds()).contains("DefaultIdHandler.execute");
    }

    @Test
    void scanDuplicateIdWarnsAndOverwrites() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(ZestComponent.class))
                .thenReturn(Map.of("dupHandler1", new DupHandler1(), "dupHandler2", new DupHandler2()));

        scanner.scan(ctx);

        // 后扫描的覆盖之前的，最终只保留 1 个
        assertThat(scanner.componentCount()).isEqualTo(1);
    }

    @Test
    void getComponentFound() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(ZestComponent.class))
                .thenReturn(Map.of("testHandler", new TestHandler()));

        scanner.scan(ctx);

        ComponentScanner.ComponentMeta meta = scanner.getComponent("doSomething");
        assertThat(meta).isNotNull();
        assertThat(meta.getExecuteId()).isEqualTo("doSomething");
        assertThat(meta.getGroupName()).isEqualTo("test");
        assertThat(meta.getComponentType()).isEqualTo(ComponentType.EXECUTOR);
        assertThat(meta.getName()).isEqualTo("执行任务");
        assertThat(meta.getDescription()).isEqualTo("测试执行方法");
        assertThat(meta.getTimeout()).isEqualTo(5000);
        assertThat(meta.isAsync()).isTrue();
    }

    @Test
    void getComponentNotFound() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(ZestComponent.class))
                .thenReturn(Map.of("testHandler", new TestHandler()));

        scanner.scan(ctx);

        ComponentScanner.ComponentMeta meta = scanner.getComponent("nonExistent");
        assertThat(meta).isNull();
    }

    @Test
    void scanNoComponents() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(ZestComponent.class)).thenReturn(Map.of());

        scanner.scan(ctx);

        assertThat(scanner.componentCount()).isEqualTo(0);
    }

    @Test
    void componentCount() {
        assertThat(scanner.componentCount()).isEqualTo(0);

        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(ZestComponent.class))
                .thenReturn(Map.of("handler", new TestHandler()));

        scanner.scan(ctx);
        assertThat(scanner.componentCount()).isEqualTo(2);
    }

    @Test
    void scanAllTypesWithMultipleMethods() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansWithAnnotation(ZestComponent.class))
                .thenReturn(Map.of("bulkHandler", new BulkTestHandler()));

        scanner.scan(ctx);

        assertThat(scanner.componentCount()).isEqualTo(50);

        // 验证每种类型的方法数和类型正确
        assertThat(scanner.getComponentIds())
                .filteredOn(id -> id.startsWith("exec_"))
                .hasSize(10)
                .allSatisfy(id -> assertThat(scanner.getComponent(id).getComponentType())
                        .isEqualTo(ComponentType.EXECUTOR));

        assertThat(scanner.getComponentIds())
                .filteredOn(id -> id.startsWith("pred_"))
                .hasSize(10)
                .allSatisfy(id -> assertThat(scanner.getComponent(id).getComponentType())
                        .isEqualTo(ComponentType.PREDICATE));

        assertThat(scanner.getComponentIds())
                .filteredOn(id -> id.startsWith("sel_"))
                .hasSize(10)
                .allSatisfy(id -> assertThat(scanner.getComponent(id).getComponentType())
                        .isEqualTo(ComponentType.SELECTOR));

        assertThat(scanner.getComponentIds())
                .filteredOn(id -> id.startsWith("load_"))
                .hasSize(10)
                .allSatisfy(id -> assertThat(scanner.getComponent(id).getComponentType())
                        .isEqualTo(ComponentType.LOADER));

        assertThat(scanner.getComponentIds())
                .filteredOn(id -> id.startsWith("parse_"))
                .hasSize(10)
                .allSatisfy(id -> assertThat(scanner.getComponent(id).getComponentType())
                        .isEqualTo(ComponentType.PARSER));
    }

    // ==================== 测试用组件类 ====================

    @ZestComponent("test")
    static class TestHandler {
        @ZestExecute(value = "doSomething", name = "执行任务", description = "测试执行方法", timeout = 5000, async = true)
        public Map<String, Object> doSomething(ChainContext ctx) {
            return Map.of("result", "ok");
        }

        @ZestExecute("doAnother")
        public Map<String, Object> doAnother(ChainContext ctx) {
            return Map.of("result", "ok");
        }

        // 没有 @ZestExecute 的方法，不应被扫描
        public Map<String, Object> notAnnotated(ChainContext ctx) {
            return Map.of("result", "ok");
        }
    }

    @ZestComponent("default")
    static class DefaultIdHandler {
        @ZestExecute("")
        public Map<String, Object> execute(ChainContext ctx) {
            return Map.of("result", "ok");
        }
    }

    @ZestComponent("dup")
    static class DupHandler1 {
        @ZestExecute("sameId")
        public Map<String, Object> method1(ChainContext ctx) {
            return Map.of("result", "ok");
        }
    }

    @ZestComponent("dup")
    static class DupHandler2 {
        @ZestExecute("sameId")
        public Map<String, Object> method2(ChainContext ctx) {
            return Map.of("result", "ok");
        }
    }

    // ==================== 批量测试：5 种类型各 10 个方法 ====================

    @ZestComponent("bulkTest")
    static class BulkTestHandler {
        // 10 × EXECUTOR
        @ZestExecute("exec_0") public void exec_0() {}
        @ZestExecute("exec_1") public void exec_1() {}
        @ZestExecute("exec_2") public void exec_2() {}
        @ZestExecute("exec_3") public void exec_3() {}
        @ZestExecute("exec_4") public void exec_4() {}
        @ZestExecute("exec_5") public void exec_5() {}
        @ZestExecute("exec_6") public void exec_6() {}
        @ZestExecute("exec_7") public void exec_7() {}
        @ZestExecute("exec_8") public void exec_8() {}
        @ZestExecute("exec_9") public void exec_9() {}

        // 10 × PREDICATE
        @ZestPredicate("pred_0") public boolean pred_0() { return true; }
        @ZestPredicate("pred_1") public boolean pred_1() { return true; }
        @ZestPredicate("pred_2") public boolean pred_2() { return true; }
        @ZestPredicate("pred_3") public boolean pred_3() { return true; }
        @ZestPredicate("pred_4") public boolean pred_4() { return true; }
        @ZestPredicate("pred_5") public boolean pred_5() { return true; }
        @ZestPredicate("pred_6") public boolean pred_6() { return true; }
        @ZestPredicate("pred_7") public boolean pred_7() { return true; }
        @ZestPredicate("pred_8") public boolean pred_8() { return true; }
        @ZestPredicate("pred_9") public boolean pred_9() { return true; }

        // 10 × SELECTOR
        @ZestSelector("sel_0") public String sel_0() { return "a"; }
        @ZestSelector("sel_1") public String sel_1() { return "b"; }
        @ZestSelector("sel_2") public String sel_2() { return "c"; }
        @ZestSelector("sel_3") public String sel_3() { return "d"; }
        @ZestSelector("sel_4") public String sel_4() { return "e"; }
        @ZestSelector("sel_5") public String sel_5() { return "f"; }
        @ZestSelector("sel_6") public String sel_6() { return "g"; }
        @ZestSelector("sel_7") public String sel_7() { return "h"; }
        @ZestSelector("sel_8") public String sel_8() { return "i"; }
        @ZestSelector("sel_9") public String sel_9() { return "j"; }

        // 10 × LOADER
        @ZestLoader("load_0") public void load_0() {}
        @ZestLoader("load_1") public void load_1() {}
        @ZestLoader("load_2") public void load_2() {}
        @ZestLoader("load_3") public void load_3() {}
        @ZestLoader("load_4") public void load_4() {}
        @ZestLoader("load_5") public void load_5() {}
        @ZestLoader("load_6") public void load_6() {}
        @ZestLoader("load_7") public void load_7() {}
        @ZestLoader("load_8") public void load_8() {}
        @ZestLoader("load_9") public void load_9() {}

        // 10 × PARSER
        @ZestParser("parse_0") public void parse_0() {}
        @ZestParser("parse_1") public void parse_1() {}
        @ZestParser("parse_2") public void parse_2() {}
        @ZestParser("parse_3") public void parse_3() {}
        @ZestParser("parse_4") public void parse_4() {}
        @ZestParser("parse_5") public void parse_5() {}
        @ZestParser("parse_6") public void parse_6() {}
        @ZestParser("parse_7") public void parse_7() {}
        @ZestParser("parse_8") public void parse_8() {}
        @ZestParser("parse_9") public void parse_9() {}
    }
}
