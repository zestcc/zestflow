package com.zestflow.executor.chain;

import com.zestflow.common.util.CodeGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ChainRepository {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<ChainPO> ROW_MAPPER = (rs, rowNum) -> {
        ChainPO po = new ChainPO();
        po.setCode(rs.getString("code"));
        try { po.setChainKey(rs.getString("chain_key")); } catch (Exception ignored) {}
        po.setName(rs.getString("name"));
        po.setDescription(rs.getString("description"));
        po.setStatus(rs.getInt("status"));
        try { po.setDesignCode(rs.getString("design_code")); } catch (Exception ignored) {}
        try { po.setVersion(rs.getInt("version")); } catch (Exception ignored) {}
        try { po.setAppCode(rs.getString("app_code")); } catch (Exception ignored) {}
        try { po.setTenantId(rs.getLong("tenant_id")); } catch (Exception ignored) {}
        po.setCreatedBy(rs.getString("created_by"));
        po.setUpdatedBy(rs.getString("updated_by"));
        po.setCreatedAt(rs.getString("created_at"));
        po.setUpdatedAt(rs.getString("updated_at"));
        try { po.setIsDeleted(rs.getInt("is_deleted")); } catch (Exception ignored) {}
        return po;
    };

    private final JdbcTemplate jdbc;
    private final long tenantId;

    public ChainRepository(JdbcTemplate jdbcTemplate, long tenantId) {
        this.jdbc = jdbcTemplate;
        this.tenantId = tenantId;
    }

    /**
     * 从绑定表查询链的设计编码
     */
    public String getDesignCode(String chainCode) {
        List<String> codes = jdbc.query(
                "SELECT design_code FROM zf_design_binding WHERE chain_code = ? AND tenant_id = ?",
                (rs, rowNum) -> rs.getString("design_code"), chainCode, tenantId);
        return codes.isEmpty() ? null : codes.get(0);
    }

    public List<ChainPO> list(String keyword, Integer status) {
        StringBuilder sql = new StringBuilder(
                "SELECT c.*, b.design_code FROM zf_chain c LEFT JOIN zf_design_binding b ON c.code = b.chain_code WHERE c.is_deleted = 0 AND c.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (c.name LIKE ? OR c.code LIKE ? OR c.chain_key LIKE ?)");
            String kw = "%" + keyword + "%";
            args.add(kw);
            args.add(kw);
            args.add(kw);
        }
        if (status != null) {
            sql.append(" AND c.status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY c.updated_at DESC");
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public ChainPO getByChainKey(String appCode, String chainKey) {
        if (chainKey == null || chainKey.isBlank()) {
            return null;
        }
        List<ChainPO> list = jdbc.query(
                "SELECT c.*, b.design_code FROM zf_chain c LEFT JOIN zf_design_binding b ON c.code = b.chain_code"
                        + " WHERE c.chain_key = ? AND c.app_code = ? AND c.is_deleted = 0 AND c.tenant_id = ?",
                ROW_MAPPER, chainKey.trim(), appCode, tenantId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 声明占位：不存在则创建 status=未设计；已存在则仅补全空 name/description。
     *
     * @return created=true 表示新建占位链
     */
    public UpsertDeclarationResult upsertDeclaration(String appCode, String chainKey, String name,
                                                      String description, String updatedBy) {
        ChainPO existing = getByChainKey(appCode, chainKey);
        String now = LocalDateTime.now().format(DTF);
        if (existing != null) {
            boolean needUpdate = (existing.getName() == null || existing.getName().isBlank())
                    && name != null && !name.isBlank();
            if (needUpdate || (description != null && !description.isBlank()
                    && (existing.getDescription() == null || existing.getDescription().isBlank()))) {
                jdbc.update("UPDATE zf_chain SET name=?, description=?, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                        needUpdate ? name : existing.getName(),
                        description != null ? description : existing.getDescription(),
                        updatedBy != null ? updatedBy : existing.getUpdatedBy(),
                        now, existing.getCode(), tenantId);
            }
            return new UpsertDeclarationResult(get(existing.getCode()), false);
        }
        String code = CodeGenerator.generate("CHN");
        String creator = updatedBy != null ? updatedBy : appCode;
        jdbc.update("INSERT INTO zf_chain(code, chain_key, name, description, status, version, app_code, tenant_id, created_by, updated_by, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                code, chainKey.trim(), name != null ? name : chainKey,
                description, ChainLifecycleStatus.DESIGNING, 1,
                appCode, tenantId, creator, creator, now, now);
        log.info("链声明占位创建 code={} chainKey={} appCode={}", code, chainKey, appCode);
        return new UpsertDeclarationResult(get(code), true);
    }

    public record UpsertDeclarationResult(ChainPO chain, boolean created) {}

    public ChainPO get(String code) {
        List<ChainPO> list = jdbc.query(
                "SELECT c.*, b.design_code FROM zf_chain c LEFT JOIN zf_design_binding b ON c.code = b.chain_code WHERE c.code = ? AND c.is_deleted = 0 AND c.tenant_id = ?",
                ROW_MAPPER, code, tenantId);
        return list.isEmpty() ? null : list.get(0);
    }

    public ChainPO create(String name, String description, String appCode, Integer status, String updatedBy) {
        String code = CodeGenerator.generate("CHN");
        String now = LocalDateTime.now().format(DTF);
        String creator = updatedBy != null ? updatedBy : (appCode != null ? appCode : "");
        jdbc.update("INSERT INTO zf_chain(code, name, description, status, version, app_code, tenant_id, created_by, updated_by, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                code, name, description, status != null ? status : 1, 1,
                appCode, tenantId, creator, creator, now, now);
        log.info("链创建成功 code={} name={} createdBy={}", code, name, creator);
        return get(code);
    }

    public ChainPO update(String code, String name, String description, Integer status, String updatedBy) {
        ChainPO cur = get(code);
        if (cur == null) return null;
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET name=?, description=?, status=?, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                name != null ? name : cur.getName(),
                description != null ? description : cur.getDescription(),
                status != null ? status : cur.getStatus(),
                updatedBy != null ? updatedBy : cur.getUpdatedBy(),
                now, code, tenantId);
        return get(code);
    }

    public ChainPO delete(String code, String updatedBy) {
        ChainPO cur = get(code);
        if (cur == null) return null;
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET is_deleted=1, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                updatedBy != null ? updatedBy : cur.getUpdatedBy(), now, code, tenantId);
        log.info("链假删成功 code={} updatedBy={}", code, updatedBy);
        return cur;
    }

    public ChainPO toggleStatus(String code, String updatedBy) {
        ChainPO cur = get(code);
        if (cur == null) return null;
        boolean hasDesign = getDesignCode(code) != null;
        int newStatus;
        if (cur.getStatus() == 0) {
            newStatus = hasDesign ? 2 : 1;
        } else {
            newStatus = 0;
        }
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET status=?, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                newStatus, updatedBy != null ? updatedBy : cur.getUpdatedBy(), now, code, tenantId);
        log.info("链状态切换 code={} newStatus={} updatedBy={}", code, newStatus, updatedBy);
        return get(code);
    }

    /** 回退绑定链的发布状态（graphData 修改且校验通过时触发） */
    public void resetBoundChainStatus(String designCode, String updatedBy) {
        jdbc.update("UPDATE zf_chain SET status = ?, updated_by = ?, updated_at = ?"
                        + " WHERE code IN (SELECT chain_code FROM zf_design_binding WHERE design_code = ? AND tenant_id = ?)"
                        + " AND status IN (?, ?) AND tenant_id = ?",
                ChainLifecycleStatus.UNPUBLISHED,
                updatedBy != null ? updatedBy : "",
                LocalDateTime.now().format(DTF),
                designCode,
                tenantId,
                ChainLifecycleStatus.PUBLISHING,
                ChainLifecycleStatus.PUBLISHED,
                tenantId);
    }

    /**
     * 热加载成功后标记链为已发布（saveGraph 会将绑定链置为未发布）。
     */
    public void markPublished(String code, String updatedBy) {
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET status=?, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                ChainLifecycleStatus.PUBLISHED,
                updatedBy != null ? updatedBy : "",
                now,
                code,
                tenantId);
        log.info("链已标记为已发布 code={} updatedBy={}", code, updatedBy);
    }

    /**
     * 设计保存后同步绑定链状态：校验通过→未发布，失败→设计中
     */
    public void syncBoundChainStatusAfterDesignSave(String designCode, boolean flowValid, String updatedBy) {
        int targetStatus = flowValid ? ChainLifecycleStatus.UNPUBLISHED : ChainLifecycleStatus.DESIGNING;
        int updated = jdbc.update("UPDATE zf_chain SET status = ?, updated_by = ?, updated_at = ?"
                        + " WHERE code IN (SELECT chain_code FROM zf_design_binding WHERE design_code = ? AND tenant_id = ?)"
                        + " AND status != ? AND tenant_id = ? AND is_deleted = 0",
                targetStatus,
                updatedBy != null ? updatedBy : "",
                LocalDateTime.now().format(DTF),
                designCode,
                tenantId,
                ChainLifecycleStatus.DISABLED,
                tenantId);
        log.info("设计保存同步链状态 designCode={} flowValid={} targetStatus={} updated={}",
                designCode, flowValid, targetStatus, updated);
    }

    // ==================== 版本化 ====================

    private static final RowMapper<ChainVersionPO> VERSION_ROW_MAPPER = (rs, rowNum) -> {
        ChainVersionPO po = new ChainVersionPO();
        po.setId(rs.getLong("id"));
        po.setChainCode(rs.getString("chain_code"));
        po.setVersion(rs.getInt("version"));
        po.setDesignCode(rs.getString("design_code"));
        po.setGraphData(rs.getString("graph_data"));
        po.setChainData(rs.getString("chain_data"));
        try { po.setAppCode(rs.getString("app_code")); } catch (Exception ignored) {}
        try { po.setTenantId(rs.getLong("tenant_id")); } catch (Exception ignored) {}
        po.setCreatedBy(rs.getString("created_by"));
        po.setCreatedAt(rs.getString("created_at"));
        return po;
    };

    /**
     * 递增链的版本号（原子操作），返回递增后的新版本号
     */
    public int incrementVersion(String code) {
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET version = version + 1, updated_at = ? WHERE code = ? AND tenant_id = ?", now, code, tenantId);
        ChainPO updated = get(code);
        return updated != null ? updated.getVersion() : 1;
    }

    /**
     * 保存版本快照
     */
    public void saveVersionSnapshot(String chainCode, int version, String designCode,
                                     String graphData, String chainData, String createdBy) {
        String now = LocalDateTime.now().format(DTF);
        ChainPO cur = get(chainCode);
        String appCode = cur != null ? cur.getAppCode() : null;
        jdbc.update("INSERT INTO zf_chain_version(chain_code, version, design_code, graph_data, chain_data, app_code, tenant_id, created_by, created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                chainCode, version, designCode, graphData, chainData,
                appCode, tenantId, createdBy != null ? createdBy : "", now);
        log.info("版本快照已保存 chainCode={} version={}", chainCode, version);
    }

    /**
     * 列出链的所有版本快照（按版本号降序）
     */
    public List<ChainVersionPO> listVersionSnapshots(String chainCode) {
        return jdbc.query("SELECT * FROM zf_chain_version WHERE chain_code = ? AND tenant_id = ? ORDER BY version DESC",
                VERSION_ROW_MAPPER, chainCode, tenantId);
    }

    /**
     * 获取指定版本的快照
     */
    public ChainVersionPO getVersionSnapshot(String chainCode, int version) {
        List<ChainVersionPO> list = jdbc.query("SELECT * FROM zf_chain_version WHERE chain_code = ? AND version = ? AND tenant_id = ?",
                VERSION_ROW_MAPPER, chainCode, version, tenantId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 回滚到指定版本：用快照数据更新绑定关系和设计数据，重置 status=2（未发布）
     *
     * @return 回滚后的 ChainPO，或 null（快照/链不存在）
     */
    public ChainPO rollbackToVersion(String code, int targetVersion, String updatedBy) {
        ChainVersionPO snapshot = getVersionSnapshot(code, targetVersion);
        if (snapshot == null) return null;
        ChainPO cur = get(code);
        if (cur == null) return null;

        String now = LocalDateTime.now().format(DTF);
        String updater = updatedBy != null ? updatedBy : cur.getUpdatedBy();

        // 更新绑定关系指向快照版本的设计
        jdbc.update("DELETE FROM zf_design_binding WHERE chain_code = ? AND tenant_id = ?", code, tenantId);
        if (snapshot.getDesignCode() != null && !snapshot.getDesignCode().isEmpty()) {
            jdbc.update("INSERT INTO zf_design_binding(design_code, chain_code, tenant_id, app_code) VALUES(?,?,?,?)",
                    snapshot.getDesignCode(), code, tenantId, null);
        }

        // 重置状态为未发布
        jdbc.update("UPDATE zf_chain SET status = 2, updated_by = ?, updated_at = ? WHERE code = ? AND tenant_id = ?",
                updater, now, code, tenantId);

        // 回写快照的 graphData/chainData 到设计表
        if (snapshot.getDesignCode() != null && !snapshot.getDesignCode().isEmpty()) {
            jdbc.update("UPDATE zf_design SET graph_data = ?, chain_data = ?, updated_by = ?, updated_at = ? WHERE code = ? AND tenant_id = ?",
                    snapshot.getGraphData(), snapshot.getChainData(), updater, now, snapshot.getDesignCode(), tenantId);
        }

        log.info("链回滚成功 code={} targetVersion={} updatedBy={}", code, targetVersion, updatedBy);
        return get(code);
    }
}
