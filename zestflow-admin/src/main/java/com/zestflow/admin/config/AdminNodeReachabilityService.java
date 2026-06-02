package com.zestflow.admin.config;

import com.zestflow.admin.model.entity.ExecutorRegistryPO;
import com.zestflow.admin.model.vo.CollectorRegistryVO;
import com.zestflow.admin.repository.ExecutorRegistryMapper;
import com.zestflow.admin.service.CollectorRegistryService;
import com.zestflow.common.constant.RegistryConstants;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 注册表节点 HTTP 探活 — 对标 Spring Boot CompositeHealthIndicator / Consul health check。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminNodeReachabilityService {

    private final RestTemplate restTemplate;
    private final CollectorRegistryService collectorRegistryService;
    private final ExecutorRegistryMapper executorRegistryMapper;

    @Value("${zestflow.admin.protocol:http}")
    private String protocol;

    @Value("${zestflow.admin.health-probe.enabled:true}")
    private boolean probeEnabled;

    @Value("${zestflow.admin.health-probe.executor-path:/health}")
    private String executorHealthPath;

    @Value("${zestflow.admin.health-probe.collector-path:/collector/health}")
    private String collectorHealthPath;

    public NodeProbeSummary probeRegisteredNodes() {
        if (!probeEnabled) {
            return NodeProbeSummary.disabled();
        }
        List<ExecutorRegistryPO> executors = executorRegistryMapper.selectList(
                new LambdaQueryWrapper<ExecutorRegistryPO>()
                        .eq(ExecutorRegistryPO::getStatus, RegistryConstants.STATUS_ONLINE));
        List<CollectorRegistryVO> collectors = collectorRegistryService.listAllOnline();

        int executorsReachable = 0;
        int executorsUnreachable = 0;
        List<String> unreachableExecutors = new ArrayList<>();
        for (ExecutorRegistryPO executor : executors) {
            String url = protocol + "://" + executor.getExecutorHost() + ":" + executor.getExecutorPort()
                    + executorHealthPath;
            if (isReachable(url)) {
                executorsReachable++;
            } else {
                executorsUnreachable++;
                unreachableExecutors.add(executor.getExecutorId());
            }
        }

        int collectorsReachable = 0;
        int collectorsUnreachable = 0;
        List<String> unreachableCollectors = new ArrayList<>();
        for (CollectorRegistryVO collector : collectors) {
            String url = protocol + "://" + collector.getCollectorHost() + ":" + collector.getCollectorPort()
                    + collectorHealthPath;
            if (isReachable(url)) {
                collectorsReachable++;
            } else {
                collectorsUnreachable++;
                unreachableCollectors.add(collector.getCollectorId());
            }
        }

        return new NodeProbeSummary(
                true,
                executors.size(),
                executorsReachable,
                executorsUnreachable,
                unreachableExecutors,
                collectors.size(),
                collectorsReachable,
                collectorsUnreachable,
                unreachableCollectors);
    }

    private boolean isReachable(String url) {
        try {
            var response = restTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (ResourceAccessException e) {
            log.debug("节点探活失败 url={}", url);
            return false;
        } catch (Exception e) {
            if (e instanceof org.springframework.web.client.HttpStatusCodeException ex
                    && ex.getStatusCode() == HttpStatus.UNAUTHORIZED) {
                // 部分节点 health 仍可达但需 token — 视为存活
                return true;
            }
            log.debug("节点探活异常 url={}", url, e);
            return false;
        }
    }

    public record NodeProbeSummary(
            boolean enabled,
            int executorsRegistered,
            int executorsReachable,
            int executorsUnreachable,
            List<String> unreachableExecutorIds,
            int collectorsRegistered,
            int collectorsReachable,
            int collectorsUnreachable,
            List<String> unreachableCollectorIds
    ) {
        static NodeProbeSummary disabled() {
            return new NodeProbeSummary(false, 0, 0, 0, List.of(), 0, 0, 0, List.of());
        }

        boolean hasUnreachableNodes() {
            return executorsUnreachable > 0 || collectorsUnreachable > 0;
        }
    }
}
