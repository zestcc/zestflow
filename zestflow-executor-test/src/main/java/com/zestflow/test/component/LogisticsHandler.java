package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ZestComponent("logistics")
public class LogisticsHandler {

    @ZestExecute(value = "createDelivery", name = "创建发货单")
    public Map<String, Object> createDelivery(ChainContext ctx) {
        log.info("物流-创建发货单");
        return Map.of("deliveryNo", "DEL" + System.currentTimeMillis(), "status", "PENDING");
    }

    @ZestExecute(value = "assignCourier", name = "分配快递员")
    public Map<String, Object> assignCourier(ChainContext ctx) {
        log.info("物流-分配快递员");
        return Map.of("courierId", 1001, "courierName", "李四");
    }

    @ZestExecute(value = "printWaybill", name = "打印运单")
    public Map<String, Object> printWaybill(ChainContext ctx) {
        log.info("物流-打印运单");
        return Map.of("waybillNo", "SF" + System.currentTimeMillis(), "printed", true);
    }

    @ZestExecute(value = "pickupPackage", name = "揽件处理")
    public Map<String, Object> pickupPackage(ChainContext ctx) {
        log.info("物流-揽件");
        return Map.of("result", "picked_up", "pickupTime", "2026-05-30 14:00:00");
    }

    @ZestExecute(value = "sortingCenter", name = "分拣中心处理")
    public Map<String, Object> sortingCenter(ChainContext ctx) {
        log.info("物流-分拣");
        return Map.of("sortingNode", "华东分拣中心", "nextStop", "杭州中转站");
    }

    @ZestExecute(value = "transportDispatch", name = "运输调度")
    public Map<String, Object> transportDispatch(ChainContext ctx) {
        log.info("物流-运输调度");
        return Map.of("vehicleNo", "浙A-88888", "driver", "王五");
    }

    @ZestExecute(value = "deliveryConfirm", name = "签收确认")
    public Map<String, Object> deliveryConfirm(ChainContext ctx) {
        log.info("物流-签收确认");
        return Map.of("result", "delivered", "signer", "赵六");
    }

    @ZestExecute(value = "returnProcess", name = "退货处理")
    public Map<String, Object> returnProcess(ChainContext ctx) {
        log.info("物流-退货处理");
        return Map.of("returnNo", "RET" + System.currentTimeMillis(), "status", "IN_TRANSIT");
    }

    @ZestExecute(value = "queryLogistics", name = "物流轨迹查询")
    public Map<String, Object> queryLogistics(ChainContext ctx) {
        log.info("物流-轨迹查询");
        return Map.of("currentNode", "杭州中转站", "nextNode", "派送中");
    }

    @ZestExecute(value = "evaluateDelivery", name = "配送评价")
    public Map<String, Object> evaluateDelivery(ChainContext ctx) {
        log.info("物流-配送评价");
        return Map.of("rating", 5, "feedback", "服务很好");
    }
}
