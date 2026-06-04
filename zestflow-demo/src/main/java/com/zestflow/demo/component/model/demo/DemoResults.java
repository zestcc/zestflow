package com.zestflow.demo.component.model.demo;

/** 演示/压测域元件返回值。 */
public final class DemoResults {
    private DemoResults() {}

    public record SeedNotifyItemsResult(java.util.List<java.util.Map<String, String>> notifyItems, int count) {}
    public record NoopStepResult(int step) {}
    public record EchoUserResult(String echoUserId, boolean valid) {}
    public record ScaleAmountResult(int scaledAmount) {}
    public record ConsumeScaledResult(int consumed) {}
    public record ReadBoundOrderResult(String orderId, String userId) {}
    public record GreetNameResult(String greeting) {}
}
