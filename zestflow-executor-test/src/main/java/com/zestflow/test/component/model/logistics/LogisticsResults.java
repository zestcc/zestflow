package com.zestflow.test.component.model.logistics;

/** 物流域元件返回值。 */
public final class LogisticsResults {
    private LogisticsResults() {}

    public record CreateDeliveryResult(String deliveryNo, String status) {}
    public record AssignCourierResult(String courierId, String courierName) {}
    public record PrintWaybillResult(String waybillNo, boolean printed) {}
    public record PickupPackageResult(String result, String pickupTime) {}
    public record SortingCenterResult(String sortingNode, String nextStop) {}
    public record TransportDispatchResult(String vehicleNo, String driver) {}
    public record DeliveryConfirmResult(String result, String signer) {}
    public record ReturnProcessResult(String returnNo, String status) {}
    public record QueryLogisticsResult(String currentNode, String nextNode) {}
    public record EvaluateDeliveryResult(int rating, String feedback) {}
}
