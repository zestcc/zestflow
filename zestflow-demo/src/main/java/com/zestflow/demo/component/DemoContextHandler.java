package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.demo.component.model.demo.DemoResults;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * 演示链路上下文种子（迭代器、子链等场景用）
 */
@Slf4j
@ZestComponent("demo")
public class DemoContextHandler {

    @ZestExecute(value = "seedNotifyItems", name = "种子通知列表")
    public DemoResults.SeedNotifyItemsResult seedNotifyItems() {
        List<Map<String, String>> items = List.of(
                Map.of("phone", "13800000001", "template", "SMS_ORDER_SHIPPED"),
                Map.of("phone", "13800000002", "template", "SMS_ORDER_SHIPPED"),
                Map.of("phone", "13800000003", "template", "SMS_ORDER_SHIPPED")
        );
        log.info("已写入 notifyItems size={}", items.size());
        return new DemoResults.SeedNotifyItemsResult(items, items.size());
    }

    @ZestExecute(value = "noopStep", name = "演示空步骤")
    public DemoResults.NoopStepResult noopStep(@ZestParam(value = "step", defaultValue = "0") int step) {
        int next = step + 1;
        return new DemoResults.NoopStepResult(next);
    }

    /** CONTINUE 策略演示：故意失败，链级 errorStrategy=CONTINUE 时后续节点仍执行 */
    @ZestExecute(value = "failStep", name = "演示失败步")
    public DemoResults.NoopStepResult failStep() {
        log.warn("演示失败步 failStep 故意抛错");
        throw new IllegalStateException("demo failStep for CONTINUE strategy");
    }
}
