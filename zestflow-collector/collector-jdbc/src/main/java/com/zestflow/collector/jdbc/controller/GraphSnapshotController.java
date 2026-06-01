package com.zestflow.collector.jdbc.controller;

import com.zestflow.collector.jdbc.config.CollectorProperties;
import com.zestflow.collector.jdbc.service.ChainGraphSnapshotService;
import com.zestflow.common.model.Result;
import com.zestflow.common.model.dto.ChainSnapshotDTO;
import com.zestflow.common.model.dto.ChainSnapshotSyncDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 图数据快照 REST 控制器 — Admin 发布时同步 + 日志查询
 */
@Slf4j
@RestController
@RequestMapping("/collector/snapshots")
@RequiredArgsConstructor
public class GraphSnapshotController {

    private final ChainGraphSnapshotService snapshotService;
    private final CollectorProperties properties;

    /**
     * Admin 发布链后同步图数据快照
     */
    @PostMapping
    public Result<?> syncSnapshot(@RequestBody ChainSnapshotSyncDTO dto,
                                   HttpServletRequest request) {
        if (!checkToken(request)) {
            return Result.fail(401, "UNAUTHORIZED", "Invalid collector token");
        }
        if (dto.getChainCode() == null || dto.getChainCode().isEmpty()) {
            return Result.fail(400, "BAD_REQUEST", "chainCode is required");
        }
        int version = snapshotService.syncSnapshot(
                dto.getChainCode(), dto.getGraphData(),
                dto.getAppCode(), dto.getCreatedBy());
        log.info("快照同步完成 chainCode={} version={}", dto.getChainCode(), version);
        return Result.success(java.util.Map.of("version", version));
    }

    /**
     * 查询指定执行时刻的图数据快照
     *
     * @param chainCode 链编码
     * @param timestamp 执行时间戳（毫秒）
     */
    @GetMapping
    public Result<?> getSnapshot(@RequestParam String chainCode,
                                  @RequestParam long timestamp,
                                  HttpServletRequest request) {
        if (!checkToken(request)) {
            return Result.fail(401, "UNAUTHORIZED", "Invalid collector token");
        }
        ChainSnapshotDTO dto = snapshotService.findSnapshotAt(chainCode, timestamp);
        if (dto == null) {
            return Result.fail(404, "NOT_FOUND", "Snapshot not found for chainCode=" + chainCode);
        }
        return Result.success(dto);
    }

    private boolean checkToken(HttpServletRequest request) {
        String token = properties.getAccessToken();
        if (token == null || token.isEmpty()) {
            return true;
        }
        String header = request.getHeader("X-Collector-Token");
        return token.equals(header);
    }
}
