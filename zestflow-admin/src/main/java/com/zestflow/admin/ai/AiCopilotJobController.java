package com.zestflow.admin.ai;

import com.zestflow.admin.ai.model.dto.AiExplainRequest;
import com.zestflow.admin.ai.model.dto.AiSuggestRequest;
import com.zestflow.admin.ai.model.vo.AiCopilotJobVO;
import com.zestflow.admin.constant.ErrorCode;
import com.zestflow.admin.service.TenantAppContext;
import com.zestflow.common.exception.BizException;
import com.zestflow.common.model.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Copilot 异步 Job API（轮询 / 断线续跑）
 */
@RestController
@RequestMapping("/ai/jobs")
@RequiredArgsConstructor
public class AiCopilotJobController {

    private final AiCopilotJobService jobService;
    private final TenantAppContext tenantAppContext;

    @PostMapping("/suggest")
    public Result<AiCopilotJobVO> submitSuggest(@RequestBody AiSuggestRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(jobService.submitSuggest(request));
    }

    @PostMapping("/explain")
    public Result<AiCopilotJobVO> submitExplain(@RequestBody AiExplainRequest request) {
        requireAppEditor(request.getAppCode());
        return Result.success(jobService.submitExplain(request));
    }

    @GetMapping("/{id}")
    public Result<AiCopilotJobVO> getJob(@PathVariable Long id) {
        return Result.success(jobService.getJob(id));
    }

    @PostMapping("/{id}/cancel")
    public Result<Void> cancelJob(@PathVariable Long id) {
        jobService.cancelJob(id);
        return Result.success();
    }

    private void requireAppEditor(String appCode) {
        if (!StringUtils.hasText(appCode)) {
            return;
        }
        if (!tenantAppContext.hasEditPermission(appCode)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
    }
}
