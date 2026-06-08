package com.zestflow.mcp;

import com.zestflow.mcp.config.McpRuntimeConfig;
import com.zestflow.mcp.config.McpRuntimeConfigParser;
import com.zestflow.mcp.delivery.DeliveryToolService;
import com.zestflow.devinit.DevInitCommandParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ZestFlow Dev Copilot MCP Server 入口（stdio）或 CLI 任务包导出。
 * <p>
 * 日志必须输出到 stderr，stdout 留给 MCP JSON-RPC 或任务包输出。
 */
public final class ZestFlowMcpApplication {

    private static final Logger log = LoggerFactory.getLogger(ZestFlowMcpApplication.class);

    private ZestFlowMcpApplication() {
    }

    public static void main(String[] args) throws Exception {
        if (DevInitCommandParser.isInitDevCommand(args)) {
            DevInitCommandParser.printResult(DevInitCommandParser.run(args));
            return;
        }

        McpRuntimeConfig config = McpRuntimeConfigParser.parse(args);

        if (isValidateDeliveryCommand(args)) {
            boolean strict = !hasArg(args, "--no-strict");
            DeliveryToolService.runValidateDeliveryCli(config.projectRoot(), config.appCode(), strict);
            return;
        }

        if (config.exportTaskPackage()) {
            ZestFlowMcpServer.exportTaskPackage(config);
            return;
        }

        log.info("Starting zestflow-mcp project={} appCode={} executorUrl={} adminUrl={} audit={}",
                config.projectRoot(), config.appCode(), config.executorUrl(), config.adminBaseUrl(),
                config.auditEnabled());

        ZestFlowMcpServer server = new ZestFlowMcpServer(config);
        Runtime.getRuntime().addShutdownHook(new Thread(server::closeGracefully, "zestflow-mcp-shutdown"));
        server.start();
        Thread.currentThread().join();
    }

    private static boolean isValidateDeliveryCommand(String[] args) {
        for (String arg : args) {
            if ("--validate-delivery".equals(arg) || "--validate_delivery".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasArg(String[] args, String flag) {
        for (String arg : args) {
            if (flag.equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
