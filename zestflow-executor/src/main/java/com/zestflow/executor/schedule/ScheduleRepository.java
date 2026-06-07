package com.zestflow.executor.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ScheduleRepository {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<SchedulePO> SCHEDULE_MAPPER = (rs, rowNum) -> SchedulePO.builder()
            .id(rs.getLong("id"))
            .chainCode(rs.getString("chain_code"))
            .chainName(rs.getString("chain_name"))
            .cron(rs.getString("cron"))
            .scheduleKind(rs.getString("schedule_kind"))
            .routeStrategy(rs.getString("route_strategy"))
            .shardTotal(rs.getInt("shard_total"))
            .shardParam(rs.getString("shard_param"))
            .misfirePolicy(rs.getString("misfire_policy"))
            .params(rs.getString("params"))
            .status(rs.getInt("status"))
            .remark(rs.getString("remark"))
            .tenantId(rs.getLong("tenant_id"))
            .appCode(rs.getString("app_code"))
            .createdBy(rs.getString("created_by"))
            .updatedBy(rs.getString("updated_by"))
            .createdAt(rs.getString("created_at"))
            .updatedAt(rs.getString("updated_at"))
            .build();

    private static final RowMapper<ScheduleLogPO> LOG_MAPPER = (rs, rowNum) -> ScheduleLogPO.builder()
            .id(rs.getLong("id"))
            .scheduleId(rs.getLong("schedule_id"))
            .chainCode(rs.getString("chain_code"))
            .executorId(rs.getString("executor_id"))
            .executionId(rs.getString("execution_id"))
            .routeStrategy(rs.getString("route_strategy"))
            .triggerType(rs.getString("trigger_type"))
            .params(rs.getString("params"))
            .status(rs.getInt("status"))
            .errorMessage(rs.getString("error_message"))
            .costMs(rs.getLong("cost_ms"))
            .triggeredAt(rs.getString("triggered_at"))
            .tenantId(rs.getLong("tenant_id"))
            .appCode(rs.getString("app_code"))
            .build();

    private final JdbcTemplate jdbc;
    private final long tenantId;
    private final String appCode;

    public ScheduleRepository(JdbcTemplate jdbc, long tenantId, String appCode) {
        this.jdbc = jdbc;
        this.tenantId = tenantId;
        this.appCode = appCode;
    }

    public List<SchedulePO> listEnabled() {
        return jdbc.query(
                "SELECT * FROM zf_schedule WHERE tenant_id = ? AND app_code = ? AND status = 1 ORDER BY id",
                SCHEDULE_MAPPER, tenantId, appCode);
    }

    public List<SchedulePO> list(String keyword, Integer status) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM zf_schedule WHERE tenant_id = ? AND app_code = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(appCode);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (chain_code LIKE ? OR chain_name LIKE ?)");
            String kw = "%" + keyword + "%";
            args.add(kw);
            args.add(kw);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY updated_at DESC, id DESC");
        return jdbc.query(sql.toString(), SCHEDULE_MAPPER, args.toArray());
    }

    public SchedulePO getById(long id) {
        List<SchedulePO> list = jdbc.query(
                "SELECT * FROM zf_schedule WHERE id = ? AND tenant_id = ? AND app_code = ?",
                SCHEDULE_MAPPER, id, tenantId, appCode);
        return list.isEmpty() ? null : list.get(0);
    }

    public long insert(SchedulePO po) {
        String now = now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO zf_schedule (chain_code, chain_name, cron, schedule_kind, route_strategy, "
                            + "shard_total, shard_param, misfire_policy, params, status, remark, tenant_id, app_code, "
                            + "created_by, updated_by, created_at, updated_at) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setString(i++, po.getChainCode());
            ps.setString(i++, po.getChainName() != null ? po.getChainName() : "");
            ps.setString(i++, po.getCron());
            ps.setString(i++, po.getScheduleKind() != null ? po.getScheduleKind() : "CRON");
            ps.setString(i++, po.getRouteStrategy() != null ? po.getRouteStrategy() : "local");
            ps.setInt(i++, po.getShardTotal() != null ? po.getShardTotal() : 1);
            ps.setString(i++, po.getShardParam());
            ps.setString(i++, po.getMisfirePolicy() != null ? po.getMisfirePolicy() : "IGNORE");
            ps.setString(i++, po.getParams());
            ps.setInt(i++, po.getStatus() != null ? po.getStatus() : 1);
            ps.setString(i++, po.getRemark());
            ps.setLong(i++, tenantId);
            ps.setString(i++, appCode);
            ps.setString(i++, po.getCreatedBy());
            ps.setString(i++, po.getUpdatedBy());
            ps.setString(i++, now);
            ps.setString(i, now);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    public void update(SchedulePO po) {
        jdbc.update(
                "UPDATE zf_schedule SET cron=?, route_strategy=?, shard_total=?, params=?, status=?, remark=?, "
                        + "updated_by=?, updated_at=? WHERE id=? AND tenant_id=? AND app_code=?",
                po.getCron(), po.getRouteStrategy(), po.getShardTotal(), po.getParams(), po.getStatus(),
                po.getRemark(), po.getUpdatedBy(), now(), po.getId(), tenantId, appCode);
    }

    public void toggleStatus(long id, int status) {
        jdbc.update(
                "UPDATE zf_schedule SET status=?, updated_at=? WHERE id=? AND tenant_id=? AND app_code=?",
                status, now(), id, tenantId, appCode);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM zf_schedule WHERE id=? AND tenant_id=? AND app_code=?", id, tenantId, appCode);
    }

    public long insertLog(ScheduleLogPO logPo) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO zf_schedule_log (schedule_id, chain_code, executor_id, execution_id, route_strategy, "
                            + "trigger_type, params, status, error_message, cost_ms, triggered_at, tenant_id, app_code) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setLong(i++, logPo.getScheduleId());
            ps.setString(i++, logPo.getChainCode());
            ps.setString(i++, logPo.getExecutorId());
            ps.setString(i++, logPo.getExecutionId());
            ps.setString(i++, logPo.getRouteStrategy());
            ps.setString(i++, logPo.getTriggerType());
            ps.setString(i++, logPo.getParams());
            ps.setInt(i++, logPo.getStatus() != null ? logPo.getStatus() : 0);
            ps.setString(i++, logPo.getErrorMessage());
            if (logPo.getCostMs() != null) {
                ps.setLong(i++, logPo.getCostMs());
            } else {
                ps.setNull(i++, java.sql.Types.BIGINT);
            }
            ps.setString(i++, logPo.getTriggeredAt());
            ps.setLong(i++, tenantId);
            ps.setString(i, appCode);
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : 0L;
    }

    public Optional<LocalDateTime> lastTriggerTime(long scheduleId) {
        List<String> rows = jdbc.query(
                "SELECT triggered_at FROM zf_schedule_log WHERE schedule_id = ? ORDER BY triggered_at DESC LIMIT 1",
                (rs, rowNum) -> rs.getString("triggered_at"), scheduleId);
        if (rows.isEmpty() || rows.get(0) == null) {
            return Optional.empty();
        }
        return Optional.of(LocalDateTime.parse(rows.get(0), DTF));
    }

    public List<ScheduleLogPO> listLogs(Long scheduleId, String keyword, Integer status) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM zf_schedule_log WHERE tenant_id = ? AND app_code = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(appCode);
        if (scheduleId != null) {
            sql.append(" AND schedule_id = ?");
            args.add(scheduleId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND chain_code LIKE ?");
            args.add("%" + keyword + "%");
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY triggered_at DESC");
        return jdbc.query(sql.toString(), LOG_MAPPER, args.toArray());
    }

    public List<ScheduleLogPO> listLogsSince(String since) {
        return jdbc.query(
                "SELECT * FROM zf_schedule_log WHERE tenant_id = ? AND app_code = ? AND triggered_at >= ? ORDER BY triggered_at DESC",
                LOG_MAPPER, tenantId, appCode, since);
    }

    public long countFailedSince(int windowMinutes) {
        String since = LocalDateTime.now().minusMinutes(windowMinutes).format(DTF);
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM zf_schedule_log WHERE tenant_id = ? AND app_code = ? AND status = 2 AND triggered_at >= ?",
                Long.class, tenantId, appCode, since);
        return count != null ? count : 0L;
    }

    private static String now() {
        return LocalDateTime.now().format(DTF);
    }
}
