package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.demo.component.model.inventory.InventoryResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("inventory")
public class InventoryHandler {

    @ZestExecute(value = "checkStock", name = "库存检查")
    public InventoryResults.CheckStockResult checkStock(
            @ZestParam(value = "productId", required = false) String productId) {
        log.info("库存-检查库存 productId={}", productId);
        return new InventoryResults.CheckStockResult(true, 200);
    }

    @ZestExecute(value = "lockStock", name = "锁定库存")
    public InventoryResults.LockStockResult lockStock(
            @ZestParam(value = "quantity", defaultValue = "5") int quantity) {
        log.info("库存-锁定库存 quantity={}", quantity);
        return new InventoryResults.LockStockResult("LCK" + System.currentTimeMillis(), quantity);
    }

    @ZestExecute(value = "unlockStock", name = "释放库存")
    public InventoryResults.UnlockStockResult unlockStock(
            @ZestParam(value = "quantity", defaultValue = "5") int quantity) {
        log.info("库存-释放库存 quantity={}", quantity);
        return new InventoryResults.UnlockStockResult(true, quantity);
    }

    @ZestExecute(value = "deductStock", name = "扣减库存")
    public InventoryResults.DeductStockResult deductStock() {
        log.info("库存-扣减库存");
        return new InventoryResults.DeductStockResult("deducted", 195);
    }

    @ZestExecute(value = "restoreStock", name = "归还库存")
    public InventoryResults.RestoreStockResult restoreStock(
            @ZestParam(value = "quantity", defaultValue = "3") int quantity) {
        log.info("库存-归还库存 quantity={}", quantity);
        return new InventoryResults.RestoreStockResult(true, quantity);
    }

    @ZestExecute(value = "queryStockDetail", name = "库存明细查询")
    public InventoryResults.QueryStockDetailResult queryStockDetail() {
        log.info("库存-明细查询");
        return new InventoryResults.QueryStockDetailResult("华东仓", "A-001", 500);
    }

    @ZestExecute(value = "transferStock", name = "库存调拨")
    public InventoryResults.TransferStockResult transferStock() {
        log.info("库存-调拨处理");
        return new InventoryResults.TransferStockResult("华东仓", "华北仓", 100);
    }

    @ZestExecute(value = "checkWarehouse", name = "仓库容量检查")
    public InventoryResults.CheckWarehouseResult checkWarehouse() {
        log.info("库存-仓库容量检查");
        return new InventoryResults.CheckWarehouseResult(0.75, true);
    }

    @ZestExecute(value = "stockWarning", name = "库存预警")
    public InventoryResults.StockWarningResult stockWarning() {
        log.info("库存-预警检查");
        return new InventoryResults.StockWarningResult(false, 50, 200);
    }

    @ZestExecute(value = "inventoryCount", name = "库存盘点")
    public InventoryResults.InventoryCountResult inventoryCount() {
        log.info("库存-盘点处理");
        return new InventoryResults.InventoryCountResult(1000, 998, -2);
    }
}
