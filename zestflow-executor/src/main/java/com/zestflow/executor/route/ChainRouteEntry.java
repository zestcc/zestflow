package com.zestflow.executor.route;

import com.zestflow.common.protocol.ChainHttpRouteConfig;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 链 HTTP 路由条目。
 */
@Getter
@AllArgsConstructor
public class ChainRouteEntry {

    private final String chainCode;
    private final ChainHttpRouteConfig config;
}
