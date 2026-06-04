package com.zestflow.executor.lifecycle;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestParamValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Parameter;
import java.util.Set;

/**
 * 默认参数校验器
 * <p>
 * 使用 Jakarta Bean Validation 对已解析的参数值进行校验。
 * 支持 {@link NotNull}、{@link NotBlank}、{@link NotEmpty} 等标准注解
 * 以及复杂对象的属性级校验。
 * <p>
 * 业务方可创建新的 {@link ZestComponent} + {@link ZestParamValidator} 实现自定义校验逻辑。
 */
@Slf4j
@ZestComponent("defaultParamValidator")
public class ParamValidator {

    private final Validator validator;

    public ParamValidator(Validator validator) {
        this.validator = validator;
    }

    @ZestParamValidator(value = "defaultParamValidator", name = "默认参数校验器")
    public void validate(Object[] args, Parameter[] params) {
        if (validator == null) {
            log.trace("Jakarta Validator 不可用，跳过校验");
            return;
        }

        for (int i = 0; i < args.length; i++) {
            Parameter param = params[i];
            Object arg = args[i];

            // 校验 @NotNull
            NotNull nn = param.getAnnotation(NotNull.class);
            if (nn != null && arg == null) {
                throw new IllegalArgumentException("参数 [" + param.getName() + "] 不能为空");
            }

            // 校验 @NotBlank
            NotBlank nb = param.getAnnotation(NotBlank.class);
            if (nb != null && (arg == null || arg.toString().trim().isEmpty())) {
                throw new IllegalArgumentException("参数 [" + param.getName() + "] 不能为空白");
            }

            // 校验 @NotEmpty
            NotEmpty ne = param.getAnnotation(NotEmpty.class);
            if (ne != null && arg == null) {
                throw new IllegalArgumentException("参数 [" + param.getName() + "] 不能为空");
            }
            if (ne != null && arg instanceof CharSequence cs && cs.isEmpty()) {
                throw new IllegalArgumentException("参数 [" + param.getName() + "] 不能为空");
            }

            // 复杂对象属性级校验
            if (arg != null) {
                Set<ConstraintViolation<Object>> violations = validator.validate(arg);
                if (!violations.isEmpty()) {
                    StringBuilder msg = new StringBuilder("参数 [" + param.getName() + "] 校验失败: ");
                    for (ConstraintViolation<?> v : violations) {
                        msg.append(v.getPropertyPath()).append(" ").append(v.getMessage()).append("; ");
                    }
                    throw new IllegalArgumentException(msg.toString());
                }
            }
        }
    }
}
