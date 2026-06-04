package com.zestflow.executor.route;

import com.zestflow.executor.chain.ChainDefinition;
import com.zestflow.executor.chain.ChainManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChainRouteRegistryTest {

    @Mock ChainManager chainManager;

    @Test
    void refreshRegistersPathAndMethod() {
        ChainDefinition def = ChainDefinition.builder()
                .code("CHN_OTA")
                .extraConfig(Map.of("http", Map.of(
                        "path", "/api/heytrip/ota/rc/getHotels",
                        "method", "POST",
                        "produces", "application/xml"
                )))
                .build();
        when(chainManager.getActiveChains()).thenReturn(Map.of("CHN_OTA", def));

        ChainRouteRegistry registry = new ChainRouteRegistry();
        registry.refresh(chainManager);

        Optional<ChainRouteEntry> entry = registry.lookup("POST", "/api/heytrip/ota/rc/getHotels");
        assertThat(entry).isPresent();
        assertThat(entry.get().getChainCode()).isEqualTo("CHN_OTA");
        assertThat(entry.get().getConfig().getProduces()).isEqualTo("application/xml");
    }

    @Test
    void lookupReturnsEmptyForUnknownPath() {
        when(chainManager.getActiveChains()).thenReturn(Map.of());
        ChainRouteRegistry registry = new ChainRouteRegistry();
        registry.refresh(chainManager);
        assertThat(registry.lookup("GET", "/unknown")).isEmpty();
    }
}
