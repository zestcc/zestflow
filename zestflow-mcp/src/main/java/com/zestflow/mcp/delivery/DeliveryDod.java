package com.zestflow.mcp.delivery;

/**
 * Delivery Definition of Done — 平台默认门禁阈值（可通过 strictMode 收紧）。
 */
public record DeliveryDod(
        int minTaskNodesProduction,
        int maxLinesPerExecute,
        double minUsableScore,
        boolean requirePatterns,
        boolean requireAcceptanceJourneys,
        boolean forbidAllInOne
) {

    public static DeliveryDod defaults() {
        return new DeliveryDod(2, 80, 0.95, true, true, true);
    }

    public static DeliveryDod relaxed() {
        return new DeliveryDod(1, 120, 0.80, false, true, false);
    }
}
