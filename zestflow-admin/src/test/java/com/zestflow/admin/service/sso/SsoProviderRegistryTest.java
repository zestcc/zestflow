package com.zestflow.admin.service.sso;

import com.zestflow.admin.config.SsoProperties;
import com.zestflow.admin.service.sso.provider.DisabledSsoProvider;
import com.zestflow.admin.service.sso.provider.GenericOidcSsoProvider;
import com.zestflow.admin.service.sso.provider.ZestSsoProvider;
import com.zestflow.admin.service.sso.spi.SsoProvider;
import com.zestflow.admin.service.sso.spi.SsoProviderRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SsoProviderRegistryTest {

    @Mock
    private ZestSsoProvider zestSsoProvider;
    @Mock
    private GenericOidcSsoProvider genericOidcSsoProvider;

    private SsoProperties properties;
    private DisabledSsoProvider disabledSsoProvider;
    private SsoProviderRegistry registry;

    @BeforeEach
    void setUp() {
        properties = new SsoProperties();
        disabledSsoProvider = new DisabledSsoProvider();
        when(zestSsoProvider.providerId()).thenReturn(ZestSsoProvider.PROVIDER_ID);
        when(genericOidcSsoProvider.providerId()).thenReturn(GenericOidcSsoProvider.PROVIDER_ID);
        registry = new SsoProviderRegistry(
                properties,
                List.of(zestSsoProvider, genericOidcSsoProvider, disabledSsoProvider),
                disabledSsoProvider);
    }

    @Test
    void resolve_whenDisabled_returnsDisabledProvider() {
        properties.setEnabled(false);
        properties.setProvider("zest-sso");

        SsoProvider provider = registry.resolve();

        assertThat(provider).isSameAs(disabledSsoProvider);
    }

    @Test
    void resolve_whenProviderNone_returnsDisabledProvider() {
        properties.setEnabled(true);
        properties.setProvider("none");

        SsoProvider provider = registry.resolve();

        assertThat(provider).isSameAs(disabledSsoProvider);
    }

    @Test
    void resolve_whenZestSsoEnabled_returnsZestSsoProvider() {
        properties.setEnabled(true);
        properties.setProvider("zest-sso");

        SsoProvider provider = registry.resolve();

        assertThat(provider).isSameAs(zestSsoProvider);
    }

    @Test
    void resolve_whenOidcEnabled_returnsGenericProvider() {
        properties.setEnabled(true);
        properties.setProvider("oidc");

        SsoProvider provider = registry.resolve();

        assertThat(provider).isSameAs(genericOidcSsoProvider);
    }
}
