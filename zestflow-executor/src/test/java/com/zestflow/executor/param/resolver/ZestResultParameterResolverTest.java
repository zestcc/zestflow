package com.zestflow.executor.param.resolver;

import com.zestflow.common.constant.ChainConstants;
import com.zestflow.executor.context.ChainContext;
import com.zestflow.executor.param.ParamConverterRegistry;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ZestResultParameterResolverTest {

    @Test
    void resolvesPredecessorResultFromMetadata() throws Exception {
        ZestResultParameterResolver resolver = new ZestResultParameterResolver(new ParamConverterRegistry());
        ChainContext ctx = new ChainContext("inst", "CHN001", Map.of());
        ctx.setMetadata(ChainConstants.META_PREDECESSOR_RESULT, "payload");

        Method method = Sample.class.getMethod("parse", String.class);
        Parameter param = method.getParameters()[0];
        assertThat(resolver.supports(param)).isTrue();
        assertThat(resolver.resolve(param, ctx)).isEqualTo("payload");
    }

    static class Sample {
        public void parse(@com.zestflow.executor.annotation.ZestResult String result) {
        }
    }
}
