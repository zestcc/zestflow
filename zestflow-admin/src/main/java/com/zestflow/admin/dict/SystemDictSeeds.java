package com.zestflow.admin.dict;

import com.zestflow.admin.model.entity.DictDataPO;

import java.util.ArrayList;
import java.util.List;

/**
 * 系统级字典种子定义（租户 1 模板，新租户通过 {@link com.zestflow.admin.tenant.TenantTemplateCloner} 克隆）。
 */
public final class SystemDictSeeds {

    private SystemDictSeeds() {}

    public record Seed(String code, String name, List<DictDataPO> items) {}

    public static List<Seed> all() {
        List<Seed> seeds = new ArrayList<>();
        seeds.add(new Seed("component_type", "元件类型", List.of(
                item("EXECUTOR", "执行器", "primary"),
                item("PREDICATE", "条件", "warning"),
                item("SELECTOR", "选择器", "warning"),
                item("LOADER", "加载器", "info"),
                item("PARSER", "解析器", ""),
                item("PRE_PROCESSOR", "前置处理器", ""),
                item("POST_PROCESSOR", "后置处理器", ""),
                item("PARAM_BINDER", "参数绑定器", ""),
                item("PARAM_VALIDATOR", "参数校验器", "")
        )));
        seeds.add(new Seed("execute_strategy", "执行策略", List.of(
                item("NORMAL", "正常", "primary"),
                item("RETRY_ON_FAILURE", "失败重试", "warning"),
                item("STOP_ON_EXCEPTION", "异常停止", "danger"),
                item("IGNORE_EXCEPTION", "忽略异常", "info")
        )));
        seeds.add(new Seed("route_strategy", "路由策略", List.of(
                item("round_robin", "轮询", "primary"),
                item("hash", "哈希", ""),
                item("random", "随机", "")
        )));
        seeds.add(new Seed("transaction_propagation", "事务传播策略", List.of(
                item("INHERIT", "继承链级", "info"),
                item("REQUIRED", "REQUIRED（加入当前事务）", "primary"),
                item("REQUIRES_NEW", "REQUIRES_NEW（独立新事务）", "warning"),
                item("NESTED", "NESTED（嵌套事务）", ""),
                item("SUPPORTS", "SUPPORTS（支持当前事务）", ""),
                item("NOT_SUPPORTED", "NOT_SUPPORTED（挂起事务）", "danger"),
                item("MANDATORY", "MANDATORY（必须在事务中）", ""),
                item("NEVER", "NEVER（禁止事务）", "")
        )));
        seeds.add(new Seed("tag_type", "标签类型", List.of(
                item("primary", "primary", "primary"),
                item("success", "success", "success"),
                item("warning", "warning", "warning"),
                item("danger", "danger", "danger"),
                item("info", "info", "info")
        )));
        seeds.add(new Seed("app_type", "应用类型", List.of()));

        // ---- 以下为原先硬编码、未入库的字典项 ----

        seeds.add(new Seed("chain_lifecycle_status", "链生命周期状态", List.of(
                item("0", "已停用", "danger"),
                item("1", "未设计", "info"),
                item("2", "未发布", "warning"),
                item("3", "发布中", "primary"),
                item("4", "已发布", "success")
        )));
        seeds.add(new Seed("enable_status", "启用状态", List.of(
                item("0", "停用", "danger"),
                item("1", "启用", "success")
        )));
        seeds.add(new Seed("yes_no", "是否", List.of(
                item("0", "否", "info"),
                item("1", "是", "success")
        )));
        seeds.add(new Seed("registry_status", "注册中心状态", List.of(
                item("0", "离线", "info"),
                item("1", "在线", "success"),
                item("2", "异常", "danger")
        )));
        seeds.add(new Seed("schedule_log_status", "调度执行状态", List.of(
                item("0", "运行中", "primary"),
                item("1", "成功", "success"),
                item("2", "失败", "danger")
        )));
        seeds.add(new Seed("schedule_job_type", "调度任务类型", List.of(
                item("CHAIN", "链路调度", "primary"),
                item("PLATFORM", "平台任务", "info")
        )));
        seeds.add(new Seed("schedule_trigger_type", "调度触发方式", List.of(
                item("cron", "定时触发", "primary"),
                item("manual", "手动触发", "warning")
        )));
        seeds.add(new Seed("platform_module", "平台任务模块", List.of(
                item("admin", "Admin", "primary"),
                item("executor", "Executor", "success"),
                item("collector", "Collector", "warning")
        )));
        seeds.add(new Seed("schedule_kind", "调度方式", List.of(
                item("CRON", "Cron 表达式", "primary"),
                item("FIXED_RATE", "固定频率", "info"),
                item("FIXED_DELAY", "固定延迟", "warning")
        )));
        seeds.add(new Seed("log_event_type", "日志事件类型", List.of(
                item("CHAIN_STARTED", "链开始", "primary"),
                item("CHAIN_COMPLETED", "链完成", "success"),
                item("CHAIN_FAILED", "链失败", "danger"),
                item("CHAIN_TIMEOUT", "链超时", "warning"),
                item("CHAIN_COMPENSATED", "链已补偿", "info"),
                item("NODE_STARTED", "节点开始", "info"),
                item("NODE_COMPLETED", "节点完成", "success"),
                item("NODE_FAILED", "节点失败", "danger"),
                item("NODE_TIMEOUT", "节点超时", "warning"),
                item("NODE_RETRYING", "节点重试中", "warning"),
                item("NODE_RETRY_EXHAUSTED", "重试耗尽", "danger"),
                item("NODE_FALLBACK_START", "降级开始", "warning"),
                item("NODE_FALLBACK_SUCCESS", "降级成功", "success"),
                item("NODE_FALLBACK_FAILED", "降级失败", "danger"),
                item("NODE_COMPENSATING", "节点补偿中", "warning"),
                item("NODE_COMPENSATED", "节点已补偿", "info")
        )));
        seeds.add(new Seed("execution_result", "执行结果", List.of(
                item("0", "失败", "danger"),
                item("1", "成功", "success")
        )));
        seeds.add(new Seed("alert_rule", "SLA 告警规则", List.of(
                item("LOW_SUCCESS_RATE", "成功率过低", "warning"),
                item("HIGH_FAIL_COUNT", "失败次数过高", "danger"),
                item("SLOW_P95", "P95 耗时过高", "warning"),
                item("NO_ONLINE_EXECUTOR", "无在线执行器", "danger"),
                item("SCHEDULE_FAILURES", "调度连续失败", "danger")
        )));
        seeds.add(new Seed("http_method", "HTTP 方法", List.of(
                item("GET", "GET", "primary"),
                item("POST", "POST", "success"),
                item("PUT", "PUT", "warning"),
                item("DELETE", "DELETE", "danger")
        )));
        seeds.add(new Seed("http_body_type", "HTTP 请求体类型", List.of(
                item("JSON", "JSON", "primary"),
                item("FORM", "FORM", "info"),
                item("RAW", "RAW", "warning")
        )));
        seeds.add(new Seed("design_line_type", "设计器连线类型", List.of(
                item("straight", "直线", "primary"),
                item("polyline", "折线", "info"),
                item("curve", "曲线", "warning")
        )));
        seeds.add(new Seed("predicate_mode", "条件判定模式", List.of(
                item("script", "脚本", "primary"),
                item("bind", "绑定元件", "info")
        )));
        seeds.add(new Seed("error_strategy", "错误策略", List.of(
                item("STOP", "失败即终止", "danger"),
                item("CONTINUE", "忽略失败继续", "warning"),
                item("COMPENSATE", "Saga 补偿", "info")
        )));
        seeds.add(new Seed("tenant_type", "租户类型", List.of(
                item("standard", "标准租户", "primary"),
                item("trial", "试玩租户", "warning")
        )));
        seeds.add(new Seed("role_code", "应用角色", List.of(
                item("APP_ADMIN", "应用管理员", "danger"),
                item("APP_EDITOR", "应用编辑", "primary"),
                item("APP_VIEWER", "应用只读", "info")
        )));
        seeds.add(new Seed("design_node_type", "设计器节点类型", List.of(
                item("start", "开始", "success"),
                item("end", "结束", "info"),
                item("NORMAL", "任务", "primary"),
                item("LOADER", "加载器", "info"),
                item("PARSER", "解析器", "info"),
                item("CONDITION", "条件", "warning"),
                item("SELECTOR", "多条件", "warning"),
                item("SCRIPT", "脚本", "primary"),
                item("SUB_CHAIN", "子链", "info"),
                item("ITERATOR", "迭代", "warning"),
                item("FORK", "并行分叉", "primary"),
                item("JOIN", "并行汇聚", "primary"),
                item("TRY_CATCH", "异常捕获", "danger"),
                item("WHILE", "条件循环", "warning"),
                item("TRANSFORMER", "数据转换", "success"),
                item("FILTER", "数据过滤", "info"),
                item("AGGREGATOR", "数据聚合", "info"),
                item("SPLITTER", "数据拆分", "info"),
                item("HTTP_CLIENT", "HTTP 调用", "primary"),
                item("MQ_PRODUCER", "消息生产", "warning"),
                item("MQ_CONSUMER", "消息消费", "success"),
                item("CACHE_READER", "缓存读取", "warning"),
                item("CACHE_WRITER", "缓存写入", "primary"),
                item("APPROVAL", "审批", "danger"),
                item("NOTIFICATION", "通知", "info"),
                item("LOGGER", "日志", "info"),
                item("DELAY", "延迟", "info")
        )));

        seeds.add(new Seed("run_location", "任务运行位置", List.of(
                item("local", "Admin 本地", "success"),
                item("remote", "远程节点", "warning")
        )));
        seeds.add(new Seed("config_value_type", "系统配置值类型", List.of(
                item("json", "JSON", "primary"),
                item("text", "文本", "info"),
                item("number", "数字", "warning"),
                item("bool", "布尔", "success")
        )));
        seeds.add(new Seed("log_analytics_time_range", "日志分析时间窗", List.of(
                item("24h", "近 24 小时", "primary"),
                item("7d", "近 7 天", "info"),
                item("30d", "近 30 天", "warning")
        )));
        seeds.add(new Seed("log_analytics_granularity", "日志趋势粒度", List.of(
                item("hour", "按小时", "primary"),
                item("day", "按天", "info")
        )));
        seeds.add(new Seed("log_analytics_rank_by", "日志排行维度", List.of(
                item("count", "执行次数", "primary"),
                item("fail", "失败次数", "danger"),
                item("slow", "平均耗时", "warning")
        )));

        // ---- AI Copilot 相关 ----

        seeds.add(new Seed("ai_provider_tier", "AI 提供商档位", List.of(
                item("A", "推荐提供商", "primary"),
                item("B", "更多提供商", "info")
        )));
        seeds.add(new Seed("ai_provider_region", "AI 提供商区域", List.of(
                item("cn", "国内", "primary"),
                item("global", "海外", "success"),
                item("local", "本地", "warning")
        )));
        seeds.add(new Seed("ai_quality_tier", "AI 模型质量档", List.of(
                item("high", "高质量", "success"),
                item("medium", "中等", "warning"),
                item("dev-only", "开发/本地", "info")
        )));
        seeds.add(new Seed("ai_recommended_for", "AI 推荐场景", List.of(
                item("chain-suggest", "链生成/修改", "primary"),
                item("explain", "链解释", "info"),
                item("expression", "表达式助手", "warning"),
                item("diagnose", "故障诊断", "danger")
        )));
        seeds.add(new Seed("ai_provider_tag", "AI 提供商标签", List.of(
                item("free-tier", "免费额度", "success"),
                item("json-friendly", "JSON 友好", "primary"),
                item("cn", "国内可用", "info")
        )));
        seeds.add(new Seed("ai_copilot_mode", "AI Copilot 模式", List.of(
                item("explain", "解释链", "info"),
                item("generate", "生成链", "primary"),
                item("modify", "修改链", "primary"),
                item("suggest", "链建议", "primary"),
                item("fix-errors", "修复校验错误", "warning"),
                item("expression", "表达式助手", "warning"),
                item("diagnose", "执行诊断", "danger"),
                item("scaffold", "元件脚手架", "success")
        )));
        seeds.add(new Seed("ai_chat_role", "AI 对话角色", List.of(
                item("user", "用户", "primary"),
                item("assistant", "助手", "success"),
                item("system", "系统", "info")
        )));
        seeds.add(new Seed("ai_usage_window_days", "AI 用量统计窗口", List.of(
                item("7", "近 7 天", "info"),
                item("30", "近 30 天", "primary"),
                item("90", "近 90 天", "warning")
        )));
        seeds.add(new Seed("ai_adoption_status", "AI 采纳状态", List.of(
                item("1", "已采纳", "success"),
                item("0", "已拒绝", "danger")
        )));

        return seeds;
    }

    private static DictDataPO item(String value, String label, String tagType) {
        DictDataPO po = new DictDataPO();
        po.setValue(value);
        po.setLabel(label);
        po.setTagType(tagType.isEmpty() ? null : tagType);
        return po;
    }
}
