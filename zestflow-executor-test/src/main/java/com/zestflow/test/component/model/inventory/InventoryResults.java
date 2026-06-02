package com.zestflow.test.component.model.inventory;

/** 库存域元件返回值。 */
public final class InventoryResults {
    private InventoryResults() {}

    public record CheckStockResult(boolean available, int stock) {}
    public record LockStockResult(String lockId, int quantity) {}
    public record UnlockStockResult(boolean released, int quantity) {}
    public record DeductStockResult(String result, int remaining) {}
    public record RestoreStockResult(boolean restored, int quantity) {}
    public record QueryStockDetailResult(String warehouse, String shelfNo, int quantity) {}
    public record TransferStockResult(String fromWarehouse, String toWarehouse, int quantity) {}
    public record CheckWarehouseResult(double usageRate, boolean available) {}
    public record StockWarningResult(boolean warning, int minStock, int currentStock) {}
    public record InventoryCountResult(int expected, int actual, int diff) {}
}
