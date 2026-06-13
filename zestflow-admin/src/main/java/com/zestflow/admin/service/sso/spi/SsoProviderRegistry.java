package com.zestflow.admin.service.sso.spi;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.service.sso.provider.DisabledSsoProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按配置选择 SSO 提供方：enabled=false 或 provider=none 时使用 {@link DisabledSsoProvider}。
 */
@Component
public class SsoProviderRegistry {

    private final SsoProperties properties;
    private final Map<String, SsoProvider> providers;
    private final DisabledSsoProvider disabledSsoProvider;

    public SsoProviderRegistry(SsoProperties properties,
                               List<SsoProvider> providerList,
                               DisabledSsoProvider disabledSsoProvider) {
        this.properties = properties;
        this.disabledSsoProvider = disabledSsoProvider;
        this.providers = providerList.stream()
                .filter(p -> !(p instanceof DisabledSsoProvider))
                .collect(Collectors.toMap(SsoProvider::providerId, Function.identity(), (a, b) -> a));
    }

    public SsoProvider resolve() {
        if (!properties.isEnabled() || isDisabledProviderId(properties.getProvider())) {
            return disabledSsoProvider;
        }
        SsoProvider provider = providers.get(normalize(properties.getProvider()));
        return provider != null ? provider : disabledSsoProvider;
    }

    private static boolean isDisabledProviderId(String provider) {
        return !StringUtils.hasText(provider) || "none".equalsIgnoreCase(provider.trim());
    }

    private static String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase();
    }
}
