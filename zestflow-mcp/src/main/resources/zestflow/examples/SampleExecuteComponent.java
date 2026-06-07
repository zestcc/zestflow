package com.zestflow.component.sample;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 标准 @ZestExecute 示例（MCP Resource 用，非自动注册）。
 */
@Slf4j
@Component
@ZestComponent("sample")
public class SampleExecuteComponent {

    @ZestExecute(value = "sampleEcho", name = "回显示例")
    public Object sampleEcho(
            @ZestParam("message") String message,
            ChainContext ctx) {
        log.debug("sampleEcho message={}", message);
        ctx.put("echo", message);
        return message;
    }
}
