package com.zestflow.common.protocol;

/**
 * HTTP 执行响应模式
 * <ul>
 *   <li>{@link #BODY} — Mode 1/2：响应体 = 链终态 PARSER 返回值</li>
 *   <li>{@link #DETAIL} — 调试/Admin：返回完整执行明细（含 instanceId、nodeResults）</li>
 * </ul>
 */
public enum ChainHttpResponseMode {
    BODY,
    DETAIL
}
