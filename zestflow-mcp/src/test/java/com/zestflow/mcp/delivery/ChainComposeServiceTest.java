package com.zestflow.mcp.delivery;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainComposeServiceTest {

    private final ChainComposeService service = new ChainComposeService();

    @Test
    void composeGuestGatedReadHasMultipleBusinessNodes() throws Exception {
        ChainComposeService.ComposeResult result = service.compose(
                "guest-gated-read", "CHN_PREVIEW", "章节试读", Map.of());
        assertEquals("guest-gated-read", result.payload().get("patternId"));
        assertEquals("production", result.payload().get("lifecycle"));
        assertTrue((Integer) result.payload().get("businessNodeCount") >= 3);
        String chainJson = (String) result.payload().get("chainDefinitionJson");
        assertTrue(chainJson.contains("\"lifecycle\" : \"production\"") || chainJson.contains("\"lifecycle\":\"production\""));
        assertTrue(chainJson.contains("_start"));
        assertTrue(chainJson.contains("_end"));
    }

    @Test
    void composeAuthOwnedWritePattern() throws Exception {
        ChainComposeService.ComposeResult result = service.compose(
                "auth-owned-write", "CHN_LISTING", "更新上架", Map.of("mutate", "updateBookListing"));
        assertTrue((Integer) result.payload().get("businessNodeCount") >= 4);
    }
}
