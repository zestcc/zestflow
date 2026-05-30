package com.zestflow.test.component;

import com.zestflow.executor.annotation.ZestComponent;
import com.zestflow.executor.annotation.ZestExecute;
import com.zestflow.executor.context.ChainContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@ZestComponent("executor")
public class ExecutorHandler {

    @ZestExecute(value = "execOrderCreate", name = "创建订单")
    public Map<String, Object> execOrderCreate(ChainContext ctx) {
        log.info("执行器-创建订单"); return Map.of("result", "created"); }

    @ZestExecute(value = "execOrderPay", name = "支付订单")
    public Map<String, Object> execOrderPay(ChainContext ctx) {
        log.info("执行器-支付订单"); return Map.of("result", "paid"); }

    @ZestExecute(value = "execOrderShip", name = "发货处理")
    public Map<String, Object> execOrderShip(ChainContext ctx) {
        log.info("执行器-发货"); return Map.of("result", "shipped"); }

    @ZestExecute(value = "execOrderConfirm", name = "确认收货")
    public Map<String, Object> execOrderConfirm(ChainContext ctx) {
        log.info("执行器-确认收货"); return Map.of("result", "confirmed"); }

    @ZestExecute(value = "execOrderRefund", name = "退款处理")
    public Map<String, Object> execOrderRefund(ChainContext ctx) {
        log.info("执行器-退款"); return Map.of("result", "refunded"); }

    @ZestExecute(value = "execUserRegister", name = "用户注册")
    public Map<String, Object> execUserRegister(ChainContext ctx) {
        log.info("执行器-用户注册"); return Map.of("result", "registered"); }

    @ZestExecute(value = "execUserLogin", name = "用户登录")
    public Map<String, Object> execUserLogin(ChainContext ctx) {
        log.info("执行器-用户登录"); return Map.of("result", "logged_in"); }

    @ZestExecute(value = "execUserLogout", name = "用户登出")
    public Map<String, Object> execUserLogout(ChainContext ctx) {
        log.info("执行器-用户登出"); return Map.of("result", "logged_out"); }

    @ZestExecute(value = "execSendNotify", name = "发送通知")
    public Map<String, Object> execSendNotify(ChainContext ctx) {
        log.info("执行器-发送通知"); return Map.of("result", "sent"); }

    @ZestExecute(value = "execSaveLog", name = "保存日志")
    public Map<String, Object> execSaveLog(ChainContext ctx) {
        log.info("执行器-保存日志"); return Map.of("result", "saved"); }
}
