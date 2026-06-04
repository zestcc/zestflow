package com.zestflow.admin.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private static final List<String> DEV_ORIGIN_PATTERNS = List.of(
            "http://localhost:8001",
            "http://127.0.0.1:8001",
            "http://localhost:5173",
            "http://127.0.0.1:5173"
    );

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
        CorsConfigurationSource source = this::resolveCorsConfiguration;
        return new CorsFilter(source);
    }

    private CorsConfiguration resolveCorsConfiguration(HttpServletRequest request) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        String origin = request.getHeader("Origin");
        // Vite 构建的 index.html 含 crossorigin，module script 会带 Origin；须放行真实同源
        if (StringUtils.hasText(origin) && isSameOrigin(request, origin)) {
            config.setAllowedOriginPatterns(List.of(origin));
            return config;
        }

        if (StringUtils.hasText(allowedOrigins)) {
            config.setAllowedOriginPatterns(parseAllowedOrigins(allowedOrigins));
        } else {
            config.setAllowedOriginPatterns(DEV_ORIGIN_PATTERNS);
        }
        return config;
    }

    private static List<String> parseAllowedOrigins(String origins) {
        return Arrays.stream(origins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    /** 识别经 Nginx 反代后的浏览器同源 Origin（含 X-Forwarded-Proto） */
    private static boolean isSameOrigin(HttpServletRequest request, String origin) {
        String host = request.getHeader("Host");
        if (!StringUtils.hasText(host)) {
            return false;
        }
        String scheme = request.getHeader("X-Forwarded-Proto");
        if (!StringUtils.hasText(scheme)) {
            scheme = request.getScheme();
        }
        String base = scheme + "://" + host;
        if (origin.equalsIgnoreCase(base)) {
            return true;
        }
        int colon = host.indexOf(':');
        if (colon > 0) {
            String hostOnly = host.substring(0, colon);
            return origin.equalsIgnoreCase(scheme + "://" + hostOnly);
        }
        return false;
    }
}
