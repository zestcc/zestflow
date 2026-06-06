package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestAggregator;
import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestFilter;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.annotation.ZestParamValidator;
import com.zestflow.executor.annotation.ZestSplitter;
import com.zestflow.executor.annotation.ZestTransformer;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据处理类元件示例
 * 覆盖 TRANSFORMER, FILTER, AGGREGATOR, SPLITTER 等元件类型
 */
@Slf4j
@ZestComponent("data")
public class DataProcessorHandler {

    @ZestTransformer(value = "transformUserInfo", name = "转换用户信息")
    public void transformUserInfo(ChainContext ctx) {
        Object userId = ctx.get("userId");
        if (userId != null) {
            ctx.put("userCode", "U-" + userId.toString().toUpperCase());
        }
        Object userName = ctx.get("userName");
        if (userName != null) {
            ctx.put("userDisplayName", userName.toString().trim());
        }
        log.debug("用户信息转换完成 userId={}", userId);
    }

    @ZestTransformer(value = "transformAmount", name = "金额单位转换")
    public void transformAmount(ChainContext ctx) {
        Object amount = ctx.get("amount");
        if (amount != null) {
            try {
                double value = Double.parseDouble(amount.toString());
                ctx.put("amountCent", (long) (value * 100));
                ctx.put("amountFormatted", String.format("%.2f", value));
            } catch (NumberFormatException e) {
                log.warn("金额转换失败 amount={}", amount);
            }
        }
    }

    @ZestTransformer(value = "transformOrderStatus", name = "订单状态转换")
    public void transformOrderStatus(ChainContext ctx) {
        Object status = ctx.get("orderStatus");
        if (status != null) {
            String statusStr = status.toString();
            String statusDesc = switch (statusStr) {
                case "PENDING" -> "待处理";
                case "PAID" -> "已支付";
                case "SHIPPED" -> "已发货";
                case "COMPLETED" -> "已完成";
                case "CANCELLED" -> "已取消";
                default -> "未知";
            };
            ctx.put("orderStatusDesc", statusDesc);
        }
    }

    @ZestFilter(value = "filterValidUsers", name = "过滤有效用户")
    public boolean filterValidUsers(
            @ZestParam(value = "userId", required = false) String userId,
            @ZestParam(value = "userStatus", required = false) String userStatus) {
        if (userId == null || userId.isBlank()) {
            log.debug("过滤无效用户: userId为空");
            return false;
        }
        if ("DISABLED".equals(userStatus)) {
            log.debug("过滤无效用户: 用户已禁用 userId={}", userId);
            return false;
        }
        log.debug("用户通过过滤 userId={}", userId);
        return true;
    }

    @ZestFilter(value = "filterHighValueOrders", name = "过滤高价值订单")
    public boolean filterHighValueOrders(
            @ZestParam(value = "amount", defaultValue = "0") double amount) {
        boolean pass = amount >= 1000;
        log.debug("订单金额过滤 amount={} pass={}", amount, pass);
        return pass;
    }

    @ZestFilter(value = "filterRiskOrders", name = "过滤风险订单")
    public boolean filterRiskOrders(
            @ZestParam(value = "riskScore", defaultValue = "0") int riskScore,
            @ZestParam(value = "riskLevel", required = false) String riskLevel) {
        if ("HIGH".equals(riskLevel)) {
            log.debug("过滤风险订单: 高风险等级");
            return false;
        }
        if (riskScore > 80) {
            log.debug("过滤风险订单: 风险评分过高 score={}", riskScore);
            return false;
        }
        return true;
    }

    @ZestAggregator(value = "aggregateOrderAmounts", name = "聚合订单金额")
    public void aggregateOrderAmounts(ChainContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) ctx.get("orderList");
        if (orders != null) {
            double totalAmount = orders.stream()
                    .mapToDouble(o -> {
                        Object amt = o.get("amount");
                        return amt != null ? Double.parseDouble(amt.toString()) : 0;
                    })
                    .sum();
            int orderCount = orders.size();
            ctx.put("totalAmount", totalAmount);
            ctx.put("orderCount", orderCount);
            ctx.put("avgAmount", orderCount > 0 ? totalAmount / orderCount : 0);
            log.info("订单金额聚合完成 count={} total={}", orderCount, totalAmount);
        }
    }

    @ZestAggregator(value = "aggregateUserStats", name = "聚合用户统计")
    public void aggregateUserStats(ChainContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> users = (List<Map<String, Object>>) ctx.get("userList");
        if (users != null) {
            long activeCount = users.stream()
                    .filter(u -> "ACTIVE".equals(u.get("status")))
                    .count();
            long newCount = users.stream()
                    .filter(u -> {
                        Object createTime = u.get("createTime");
                        return createTime != null && createTime.toString().contains("2024");
                    })
                    .count();
            ctx.put("activeUserCount", activeCount);
            ctx.put("newUserCount", newCount);
            log.info("用户统计聚合完成 active={} new={}", activeCount, newCount);
        }
    }

    @ZestSplitter(value = "splitOrdersByStatus", name = "按状态拆分订单")
    public void splitOrdersByStatus(ChainContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) ctx.get("orderList");
        if (orders != null) {
            List<Map<String, Object>> pendingOrders = orders.stream()
                    .filter(o -> "PENDING".equals(o.get("status")))
                    .collect(Collectors.toList());
            List<Map<String, Object>> completedOrders = orders.stream()
                    .filter(o -> "COMPLETED".equals(o.get("status")))
                    .collect(Collectors.toList());
            ctx.put("pendingOrders", pendingOrders);
            ctx.put("completedOrders", completedOrders);
            log.info("订单按状态拆分完成 pending={} completed={}", pendingOrders.size(), completedOrders.size());
        }
    }

    @ZestSplitter(value = "splitItemsByType", name = "按类型拆分商品")
    public void splitItemsByType(ChainContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) ctx.get("itemList");
        if (items != null) {
            List<Map<String, Object>> physicalItems = items.stream()
                    .filter(o -> "PHYSICAL".equals(o.get("type")))
                    .collect(Collectors.toList());
            List<Map<String, Object>> digitalItems = items.stream()
                    .filter(o -> "DIGITAL".equals(o.get("type")))
                    .collect(Collectors.toList());
            ctx.put("physicalItems", physicalItems);
            ctx.put("digitalItems", digitalItems);
            log.info("商品按类型拆分完成 physical={} digital={}", physicalItems.size(), digitalItems.size());
        }
    }

    private ChainContext extractContext(Object[] args) {
        if (args == null) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof ChainContext ctx) {
                return ctx;
            }
        }
        return null;
    }

    @ZestParamValidator(value = "validateOrderParams", name = "校验订单参数")
    public void validateOrderParams(Object[] args, Parameter[] params) {
        ChainContext ctx = extractContext(args);
        if (ctx == null) {
            return;
        }
        Object userId = ctx.get("userId");
        Object productId = ctx.get("productId");
        Object quantity = ctx.get("quantity");
        if (userId == null || userId.toString().isBlank()) {
            throw new IllegalArgumentException("userId不能为空");
        }
        if (productId == null || productId.toString().isBlank()) {
            throw new IllegalArgumentException("productId不能为空");
        }
        if (quantity != null) {
            int qty = Integer.parseInt(quantity.toString());
            if (qty <= 0) {
                throw new IllegalArgumentException("quantity必须大于0");
            }
            if (qty > 100) {
                throw new IllegalArgumentException("quantity不能超过100");
            }
        }
    }

    @ZestParamValidator(value = "validatePaymentParams", name = "校验支付参数")
    public void validatePaymentParams(Object[] args, Parameter[] params) {
        ChainContext ctx = extractContext(args);
        if (ctx == null) {
            return;
        }
        Object amount = ctx.get("amount");
        Object payType = ctx.get("payType");
        if (amount != null) {
            double amt = Double.parseDouble(amount.toString());
            if (amt <= 0) {
                throw new IllegalArgumentException("amount必须大于0");
            }
            if (amt > 100000) {
                throw new IllegalArgumentException("amount不能超过100000");
            }
        }
        if (payType != null && !payType.toString().isBlank()) {
            if (!List.of("ALIPAY", "WECHAT", "BANK").contains(payType.toString())) {
                throw new IllegalArgumentException("payType必须是ALIPAY、WECHAT或BANK");
            }
        }
    }

    @ZestExecute(value = "generateBatchIds", name = "生成批量ID")
    public List<String> generateBatchIds(
            @ZestParam(value = "prefix", defaultValue = "BATCH") String prefix,
            @ZestParam(value = "count", defaultValue = "10") int count) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(prefix + "-" + System.currentTimeMillis() + "-" + i);
        }
        log.info("生成批量ID prefix={} count={}", prefix, count);
        return ids;
    }
}