package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.demo.component.model.logistics.LogisticsResults;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ZestComponent("logistics")
public class LogisticsHandler {

    @ZestExecute(value = "createDelivery", name = "创建配送单")
    public LogisticsResults.CreateDeliveryResult createDelivery(
            @ZestParam(value = "orderId", required = false) String orderId) {
        log.info("物流-创建配送单 orderId={}", orderId);
        return new LogisticsResults.CreateDeliveryResult("DLV" + System.currentTimeMillis(), "CREATED");
    }

    @ZestExecute(value = "assignCourier", name = "分配快递员")
    public LogisticsResults.AssignCourierResult assignCourier() {
        log.info("物流-分配快递员");
        return new LogisticsResults.AssignCourierResult("C001", "张三");
    }

    @ZestExecute(value = "printWaybill", name = "打印运单")
    public LogisticsResults.PrintWaybillResult printWaybill() {
        log.info("物流-打印运单");
        return new LogisticsResults.PrintWaybillResult("WB" + System.currentTimeMillis(), true);
    }

    @ZestExecute(value = "pickupPackage", name = "揽收包裹")
    public LogisticsResults.PickupPackageResult pickupPackage() {
        log.info("物流-揽收包裹");
        return new LogisticsResults.PickupPackageResult("picked", "2026-05-30 14:00");
    }

    @ZestExecute(value = "sortingCenter", name = "分拣中心")
    public LogisticsResults.SortingCenterResult sortingCenter() {
        log.info("物流-分拣中心");
        return new LogisticsResults.SortingCenterResult("上海分拣中心", "杭州转运");
    }

    @ZestExecute(value = "transportDispatch", name = "运输调度")
    public LogisticsResults.TransportDispatchResult transportDispatch() {
        log.info("物流-运输调度");
        return new LogisticsResults.TransportDispatchResult("沪A12345", "李四");
    }

    @ZestExecute(value = "deliveryConfirm", name = "签收确认")
    public LogisticsResults.DeliveryConfirmResult deliveryConfirm() {
        log.info("物流-签收确认");
        return new LogisticsResults.DeliveryConfirmResult("signed", "王五");
    }

    @ZestExecute(value = "returnProcess", name = "退货物流")
    public LogisticsResults.ReturnProcessResult returnProcess() {
        log.info("物流-退货处理");
        return new LogisticsResults.ReturnProcessResult("RET" + System.currentTimeMillis(), "IN_TRANSIT");
    }

    @ZestExecute(value = "queryLogistics", name = "物流查询")
    public LogisticsResults.QueryLogisticsResult queryLogistics(
            @ZestParam(value = "orderId", required = false) String orderId) {
        log.info("物流-轨迹查询 orderId={}", orderId);
        return new LogisticsResults.QueryLogisticsResult("上海转运中心", "杭州配送站");
    }

    @ZestExecute(value = "evaluateDelivery", name = "配送评价")
    public LogisticsResults.EvaluateDeliveryResult evaluateDelivery() {
        log.info("物流-配送评价");
        return new LogisticsResults.EvaluateDeliveryResult(5, "配送及时");
    }
}
