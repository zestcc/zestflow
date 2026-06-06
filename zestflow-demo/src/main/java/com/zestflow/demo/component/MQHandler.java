package com.zestflow.demo.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.annotation.ZestParam;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * MQ消息处理类元件示例
 * 模拟 MQ_PRODUCER 和 MQ_CONSUMER 的使用场景
 */
@Slf4j
@ZestComponent("mq")
public class MQHandler {

    // 模拟消息队列
    private static final Map<String, ConcurrentLinkedQueue<Map<String, Object>>> queues = new ConcurrentHashMap<>();

    @ZestExecute(value = "sendOrderCreatedMsg", name = "发送订单创建消息")
    public String sendOrderCreatedMsg(
            @ZestParam(value = "orderId") String orderId,
            @ZestParam(value = "userId") String userId,
            @ZestParam(value = "amount") double amount) {
        String queueName = "order.created";
        Map<String, Object> message = Map.of(
                "orderId", orderId,
                "userId", userId,
                "amount", amount,
                "timestamp", System.currentTimeMillis(),
                "messageType", "ORDER_CREATED"
        );
        sendMessage(queueName, message);
        log.info("发送订单创建消息 queue={} orderId={}", queueName, orderId);
        return orderId;
    }

    @ZestExecute(value = "sendPaymentSuccessMsg", name = "发送支付成功消息")
    public String sendPaymentSuccessMsg(
            @ZestParam(value = "paymentId") String paymentId,
            @ZestParam(value = "orderId") String orderId,
            @ZestParam(value = "amount") double amount) {
        String queueName = "payment.success";
        Map<String, Object> message = Map.of(
                "paymentId", paymentId,
                "orderId", orderId,
                "amount", amount,
                "timestamp", System.currentTimeMillis(),
                "messageType", "PAYMENT_SUCCESS"
        );
        sendMessage(queueName, message);
        log.info("发送支付成功消息 queue={} paymentId={}", queueName, paymentId);
        return paymentId;
    }

    @ZestExecute(value = "sendInventoryUpdateMsg", name = "发送库存更新消息")
    public String sendInventoryUpdateMsg(
            @ZestParam(value = "productId") String productId,
            @ZestParam(value = "quantity") int quantity,
            @ZestParam(value = "operation", defaultValue = "DEDUCT") String operation) {
        String queueName = "inventory.update";
        Map<String, Object> message = Map.of(
                "productId", productId,
                "quantity", quantity,
                "operation", operation,
                "timestamp", System.currentTimeMillis(),
                "messageType", "INVENTORY_UPDATE"
        );
        sendMessage(queueName, message);
        log.info("发送库存更新消息 queue={} productId={} operation={}", queueName, productId, operation);
        return productId;
    }

    @ZestExecute(value = "sendRefundMsg", name = "发送退款消息")
    public String sendRefundMsg(
            @ZestParam(value = "refundId") String refundId,
            @ZestParam(value = "orderId") String orderId,
            @ZestParam(value = "amount") double amount) {
        String queueName = "refund.created";
        Map<String, Object> message = Map.of(
                "refundId", refundId,
                "orderId", orderId,
                "amount", amount,
                "timestamp", System.currentTimeMillis(),
                "messageType", "REFUND_CREATED"
        );
        sendMessage(queueName, message);
        log.info("发送退款消息 queue={} refundId={}", queueName, refundId);
        return refundId;
    }

    @ZestExecute(value = "sendNotificationMsg", name = "发送通知消息")
    public String sendNotificationMsg(
            @ZestParam(value = "userId") String userId,
            @ZestParam(value = "template") String template,
            @ZestParam(value = "content") String content) {
        String queueName = "notification.send";
        Map<String, Object> message = Map.of(
                "userId", userId,
                "template", template,
                "content", content,
                "timestamp", System.currentTimeMillis(),
                "messageType", "NOTIFICATION"
        );
        sendMessage(queueName, message);
        log.info("发送通知消息 queue={} userId={} template={}", queueName, userId, template);
        return userId;
    }

    @ZestExecute(value = "consumeOrderCreatedMsg", name = "消费订单创建消息")
    public List<Map<String, Object>> consumeOrderCreatedMsg(
            @ZestParam(value = "batchSize", defaultValue = "10") int batchSize) {
        String queueName = "order.created";
        List<Map<String, Object>> messages = consumeMessages(queueName, batchSize);
        log.info("消费订单创建消息 queue={} count={}", queueName, messages.size());
        return messages;
    }

    @ZestExecute(value = "consumePaymentSuccessMsg", name = "消费支付成功消息")
    public List<Map<String, Object>> consumePaymentSuccessMsg(
            @ZestParam(value = "batchSize", defaultValue = "10") int batchSize) {
        String queueName = "payment.success";
        List<Map<String, Object>> messages = consumeMessages(queueName, batchSize);
        log.info("消费支付成功消息 queue={} count={}", queueName, messages.size());
        return messages;
    }

    @ZestExecute(value = "consumeInventoryUpdateMsg", name = "消费库存更新消息")
    public List<Map<String, Object>> consumeInventoryUpdateMsg(
            @ZestParam(value = "batchSize", defaultValue = "10") int batchSize) {
        String queueName = "inventory.update";
        List<Map<String, Object>> messages = consumeMessages(queueName, batchSize);
        log.info("消费库存更新消息 queue={} count={}", queueName, messages.size());
        return messages;
    }

    @ZestExecute(value = "consumeRefundMsg", name = "消费退款消息")
    public List<Map<String, Object>> consumeRefundMsg(
            @ZestParam(value = "batchSize", defaultValue = "10") int batchSize) {
        String queueName = "refund.created";
        List<Map<String, Object>> messages = consumeMessages(queueName, batchSize);
        log.info("消费退款消息 queue={} count={}", queueName, messages.size());
        return messages;
    }

    @ZestExecute(value = "consumeNotificationMsg", name = "消费通知消息")
    public List<Map<String, Object>> consumeNotificationMsg(
            @ZestParam(value = "batchSize", defaultValue = "10") int batchSize) {
        String queueName = "notification.send";
        List<Map<String, Object>> messages = consumeMessages(queueName, batchSize);
        log.info("消费通知消息 queue={} count={}", queueName, messages.size());
        return messages;
    }

    @ZestExecute(value = "sendBatchMessages", name = "批量发送消息")
    public int sendBatchMessages(
            @ZestParam(value = "queueName") String queueName,
            ChainContext ctx) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> messages = (List<Map<String, Object>>) ctx.get("messageList");
        if (messages == null || messages.isEmpty()) {
            log.warn("批量发送消息: 消息列表为空");
            return 0;
        }
        int count = 0;
        for (Map<String, Object> message : messages) {
            sendMessage(queueName, message);
            count++;
        }
        log.info("批量发送消息完成 queue={} count={}", queueName, count);
        return count;
    }

    @ZestExecute(value = "getQueueSize", name = "获取队列大小")
    public int getQueueSize(@ZestParam(value = "queueName") String queueName) {
        ConcurrentLinkedQueue<Map<String, Object>> queue = queues.get(queueName);
        int size = queue != null ? queue.size() : 0;
        log.info("获取队列大小 queue={} size={}", queueName, size);
        return size;
    }

    @ZestExecute(value = "clearQueue", name = "清空队列")
    public int clearQueue(@ZestParam(value = "queueName") String queueName) {
        ConcurrentLinkedQueue<Map<String, Object>> queue = queues.get(queueName);
        int size = queue != null ? queue.size() : 0;
        if (queue != null) {
            queue.clear();
        }
        log.info("清空队列 queue={} cleared={}", queueName, size);
        return size;
    }

    private void sendMessage(String queueName, Map<String, Object> message) {
        queues.computeIfAbsent(queueName, k -> new ConcurrentLinkedQueue<>()).offer(message);
    }

    private List<Map<String, Object>> consumeMessages(String queueName, int batchSize) {
        ConcurrentLinkedQueue<Map<String, Object>> queue = queues.get(queueName);
        if (queue == null) {
            return List.of();
        }
        List<Map<String, Object>> messages = new ArrayList<>();
        for (int i = 0; i < batchSize; i++) {
            Map<String, Object> message = queue.poll();
            if (message == null) {
                break;
            }
            messages.add(message);
        }
        return messages;
    }
}