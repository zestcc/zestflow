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
        po.setName(rs.getString("name"));
        po.setDescription(rs.getString("description"));
        po.setStatus(rs.getInt("status"));
        po.setDesignCode(rs.getString("design_code"));
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

    public ChainRepository(JdbcTemplate jdbcTemplate) {
        this.jdbc = jdbcTemplate;
    }

    public List<ChainPO> list(String keyword, Integer status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM zf_chain WHERE is_deleted = 0");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (name LIKE ? OR code LIKE ?)");
            String kw = "%" + keyword + "%";
            args.add(kw);
            args.add(kw);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY updated_at DESC");
        return jdbc.query(sql.toString(), ROW_MAPPER, args.toArray());
    }

    public ChainPO get(String code) {
        List<ChainPO> list = jdbc.query("SELECT * FROM zf_chain WHERE code = ? AND is_deleted = 0", ROW_MAPPER, code);
        return list.isEmpty() ? null : list.get(0);
    }

    public ChainPO create(String name, String description, String appCode, Integer status, String updatedBy) {
        String code = CodeGenerator.generate("CHN");
        String now = LocalDateTime.now().format(DTF);
        String creator = updatedBy != null ? updatedBy : (appCode != null ? appCode : "");
        jdbc.update("INSERT INTO zf_chain(code, name, description, status, version, app_code, tenant_id, created_by, updated_by, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                code, name, description, status != null ? status : 1, 1,
                appCode, 1L, creator, creator, now, now);
        log.info("链创建成功 code={} name={} createdBy={}", code, name, creator);
        return get(code);
    }

    public ChainPO update(String code, String name, String description, Integer status, String updatedBy) {
        ChainPO cur = get(code);
        if (cur == null) return null;
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET name=?, description=?, status=?, updated_by=?, updated_at=? WHERE code=?",
                name != null ? name : cur.getName(),
                description != null ? description : cur.getDescription(),
                status != null ? status : cur.getStatus(),
                updatedBy != null ? updatedBy : cur.getUpdatedBy(),
                now, code);
        return get(code);
    }

    public ChainPO delete(String code, String updatedBy) {
        ChainPO cur = get(code);
        if (cur == null) return null;
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET is_deleted=1, updated_by=?, updated_at=? WHERE code=?",
                updatedBy != null ? updatedBy : cur.getUpdatedBy(), now, code);
        log.info("链假删成功 code={} updatedBy={}", code, updatedBy);
        return cur;
    }

    public ChainPO toggleStatus(String code, String updatedBy) {
        ChainPO cur = get(code);
        if (cur == null) return null;
        boolean hasDesign = cur.getDesignCode() != null && !cur.getDesignCode().isEmpty();
        int newStatus;
        if (cur.getStatus() == 0) {
            newStatus = hasDesign ? 2 : 1;
        } else {
            newStatus = 0;
        }
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET status=?, updated_by=?, updated_at=? WHERE code=?",
                newStatus, updatedBy != null ? updatedBy : cur.getUpdatedBy(), now, code);
        log.info("链状态切换 code={} newStatus={} updatedBy={}", code, newStatus, updatedBy);
        return get(code);
    }

    public void updateDesignCode(String code, String designCode, String updatedBy) {
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET design_code=?, updated_by=?, updated_at=? WHERE code=?",
                designCode, updatedBy != null ? updatedBy : "", now, code);
    }

    public void updateStatusAndDesignCode(String code, int status, String designCode, String updatedBy) {
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET status=?, design_code=?, updated_by=?, updated_at=? WHERE code=?",
                status, designCode, updatedBy != null ? updatedBy : "", now, code);
    }

    /** 回退绑定链的发布状态（graphData 修改时触发） */
    public void resetBoundChainStatus(String designCode, String updatedBy) {
        jdbc.update("UPDATE zf_chain SET status = 2, updated_by = ?, updated_at = ? WHERE design_code = ? AND status IN (3, 4)",
                updatedBy != null ? updatedBy : "", LocalDateTime.now().format(DTF), designCode);
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
        jdbc.update("UPDATE zf_chain SET version = version + 1, updated_at = ? WHERE code = ?", now, code);
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
                appCode, 1L, createdBy != null ? createdBy : "", now);
        log.info("版本快照已保存 chainCode={} version={}", chainCode, version);
    }

    /**
     * 列出链的所有版本快照（按版本号降序）
     */
    public List<ChainVersionPO> listVersionSnapshots(String chainCode) {
        return jdbc.query("SELECT * FROM zf_chain_version WHERE chain_code = ? ORDER BY version DESC",
                VERSION_ROW_MAPPER, chainCode);
    }

    /**
     * 获取指定版本的快照
     */
    public ChainVersionPO getVersionSnapshot(String chainCode, int version) {
        List<ChainVersionPO> list = jdbc.query("SELECT * FROM zf_chain_version WHERE chain_code = ? AND version = ?",
                VERSION_ROW_MAPPER, chainCode, version);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 回滚到指定版本：用快照数据覆盖当前链的 design_code，重置 status=2（未发布）
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

        // 回退设计编码、重置状态为未发布
        jdbc.update("UPDATE zf_chain SET design_code = ?, status = 2, updated_by = ?, updated_at = ? WHERE code = ?",
                snapshot.getDesignCode(), updater, now, code);

        // 同时回写快照的 graphData/chainData 到设计表
        if (snapshot.getDesignCode() != null && !snapshot.getDesignCode().isEmpty()) {
            jdbc.update("UPDATE zf_design SET graph_data = ?, chain_data = ?, updated_by = ?, updated_at = ? WHERE code = ?",
                    snapshot.getGraphData(), snapshot.getChainData(), updater, now, snapshot.getDesignCode());
        }

        log.info("链回滚成功 code={} targetVersion={} updatedBy={}", code, targetVersion, updatedBy);
        return get(code);
    }
}
