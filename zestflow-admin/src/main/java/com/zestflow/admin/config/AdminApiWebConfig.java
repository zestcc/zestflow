package com.zestflow.admin.config;

import com.zestflow.common.constant.AdminApiPaths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Admin REST API 统一挂载在 {@link AdminApiPaths#PREFIX} 下；
 * Controller 仍使用相对路径（如 {@code /chains}、{@code /auth}）。
 */
@Configuration
public class AdminApiWebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(AdminApiPaths.PREFIX,
                HandlerTypePredicate.forAnnotation(RestController.class)
                        .and(HandlerTypePredicate.forBasePackage("com.zestflow.admin")));
    }
}
