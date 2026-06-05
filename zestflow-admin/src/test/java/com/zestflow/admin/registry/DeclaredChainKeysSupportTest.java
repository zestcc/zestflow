package com.zestflow.admin.registry;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DeclaredChainKeysSupportTest {

    @Test
    void roundTripJson() {
        List<String> keys = List.of("demo.orders.afterSale", "heytrip.ota.getHotels");
        String json = DeclaredChainKeysSupport.toJson(keys);
        assertThat(DeclaredChainKeysSupport.fromJson(json)).containsExactlyElementsOf(keys);
    }

    @Test
    void normalizeTrimsAndDedupes() {
        List<String> normalized = DeclaredChainKeysSupport.normalize(List.of(" a ", "a", "", "b"));
        assertThat(normalized).containsExactly("a", "b");
    }
}
