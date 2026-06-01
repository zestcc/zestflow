package com.zestflow.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${zestflow.admin.cors.allowed-origins:}")
    private String allowedOrigins;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/")
                .setCachePeriod(3600);
    }

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 生产环境：前端内嵌在 Admin 中（同源），无需配置 CORS
        // 开发环境：前端单独启动（如 localhost:8001），需配置对应来源
        if (StringUtils.hasText(allowedOrigins)) {
            config.setAllowedOriginPatterns(List.of(allowedOrigins.split(",")));
        } else {
            // 默认放行同源请求 + 常见开发端口
            config.setAllowedOriginPatterns(List.of(
                    "http://localhost:8001",
                    "http://127.0.0.1:8001",
                    "http://localhost:5173",
                    "http://127.0.0.1:5173"
            ));
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
