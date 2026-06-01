package com.zestflow.executor.design;

import com.zestflow.common.util.CodeGenerator;
import com.zestflow.executor.chain.ChainPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DesignRepository {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<DesignPO> DESIGN_ROW_MAPPER = (rs, rowNum) -> {
        DesignPO po = new DesignPO();
        po.setCode(rs.getString("code"));
        po.setName(rs.getString("name"));
        po.setDescription(rs.getString("description"));
        po.setDesigner(rs.getString("designer"));
        po.setStatus(rs.getInt("status"));
        po.setGraphData(rs.getString("graph_data"));
        try { po.setChainData(rs.getString("chain_data")); } catch (Exception ignored) {}
        try { po.setAppCode(rs.getString("app_code")); } catch (Exception ignored) {}
        try { po.setTenantId(rs.getLong("tenant_id")); } catch (Exception ignored) {}
        po.setCreatedBy(rs.getString("created_by"));
        po.setUpdatedBy(rs.getString("updated_by"));
        po.setCreatedAt(rs.getString("created_at"));
        po.setUpdatedAt(rs.getString("updated_at"));
        try { po.setIsDeleted(rs.getInt("is_deleted")); } catch (Exception ignored) {}
        return po;
    };

    private static final RowMapper<ChainPO> CHAIN_ROW_MAPPER = (rs, rowNum) -> {
        ChainPO po = new ChainPO();
        po.setCode(rs.getString("code"));
        po.setName(rs.getString("name"));
        po.setDescription(rs.getString("description"));
        po.setStatus(rs.getInt("status"));
        try { po.setDesignCode(rs.getString("design_code")); } catch (Exception ignored) {}
        try { po.setAppCode(rs.getString("app_code")); } catch (Exception ignored) {}
        try { po.setTenantId(rs.getLong("tenant_id")); } catch (Exception ignored) {}
        po.setCreatedBy(rs.getString("created_by"));
        po.setUpdatedBy(rs.getString("updated_by"));
        po.setCreatedAt(rs.getString("created_at"));
        po.setUpdatedAt(rs.getString("updated_at"));
        return po;
    };

    private final JdbcTemplate jdbc;
    private final long tenantId;

    public DesignRepository(JdbcTemplate jdbcTemplate, long tenantId) {
        this.jdbc = jdbcTemplate;
        this.tenantId = tenantId;
    }

    // ==================== 设计 CRUD ====================

    public List<DesignPO> list(String keyword, Integer status) {
        StringBuilder sql = new StringBuilder("SELECT * FROM zf_design WHERE is_deleted = 0 AND tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
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
        return jdbc.query(sql.toString(), DESIGN_ROW_MAPPER, args.toArray());
    }

    public DesignPO get(String code) {
        List<DesignPO> list = jdbc.query("SELECT * FROM zf_design WHERE code = ? AND is_deleted = 0 AND tenant_id = ?", DESIGN_ROW_MAPPER, code, tenantId);
        return list.isEmpty() ? null : list.get(0);
    }

    public DesignPO create(String name, String description, String designer, String appCode, String graphData,
                           String chainData, String updatedBy) {
        String code = CodeGenerator.generate("DSN");
        String now = LocalDateTime.now().format(DTF);
        String creator = updatedBy != null ? updatedBy : (appCode != null ? appCode : "");
        jdbc.update("INSERT INTO zf_design(code, name, description, designer, status, graph_data, chain_data, app_code, tenant_id, created_by, updated_by, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                code, name, description, designer, 1, graphData, chainData,
                appCode, tenantId, creator, creator, now, now);
        log.info("设计创建成功 code={} name={} createdBy={}", code, name, creator);
        return get(code);
    }

    public DesignPO update(String code, String name, String description, String designer, Integer status,
                           String updatedBy) {
        DesignPO cur = get(code);
        if (cur == null) return null;
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_design SET name=?, description=?, designer=?, status=?, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                name != null ? name : cur.getName(),
                description != null ? description : cur.getDescription(),
                designer != null ? designer : cur.getDesigner(),
                status != null ? status : cur.getStatus(),
                updatedBy != null ? updatedBy : cur.getUpdatedBy(),
                now, code, tenantId);
        return get(code);
    }

    public DesignPO saveGraph(String code, String graphData, String chainData, String updatedBy) {
        DesignPO cur = get(code);
        if (cur == null) return null;
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_design SET graph_data=?, chain_data=?, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                graphData, chainData, updatedBy != null ? updatedBy : cur.getUpdatedBy(), now, code, tenantId);
        log.info("设计图保存成功 code={}", code);
        return get(code);
    }

    public DesignPO delete(String code, String updatedBy) {
        DesignPO cur = get(code);
        if (cur == null) return null;
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_design SET is_deleted=1, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                updatedBy != null ? updatedBy : cur.getUpdatedBy(), now, code, tenantId);
        log.info("设计假删成功 code={} updatedBy={}", code, updatedBy);
        return cur;
    }

    public DesignPO toggleStatus(String code, String updatedBy) {
        DesignPO cur = get(code);
        if (cur == null) return null;
        int newStatus = (cur.getStatus() != null && cur.getStatus() == 1) ? 0 : 1;
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_design SET status=?, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                newStatus, updatedBy != null ? updatedBy : cur.getUpdatedBy(), now, code, tenantId);
        log.info("设计状态切换 code={} newStatus={}", code, newStatus);
        return get(code);
    }

    // ==================== 绑定关系 ====================

    public List<ChainPO> getBindings(String designCode) {
        return jdbc.query(
                "SELECT c.*, b.design_code FROM zf_chain c INNER JOIN zf_design_binding b ON c.code = b.chain_code WHERE b.design_code = ? AND c.tenant_id = ? ORDER BY c.updated_at DESC",
                CHAIN_ROW_MAPPER, designCode, tenantId);
    }

    public boolean bind(String designCode, String chainCode, String updatedBy) {
        // 先清除该链的旧绑定，避免残留
        jdbc.update("DELETE FROM zf_design_binding WHERE chain_code = ?", chainCode);
        // 创建新绑定
        int updated = jdbc.update("INSERT INTO zf_design_binding(design_code, chain_code) VALUES(?,?)",
                designCode, chainCode);
        if (updated == 0) return false;
        // 更新链状态为未发布
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET status=2, updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                updatedBy != null ? updatedBy : "", now, chainCode, tenantId);
        log.info("设计绑定成功 designCode={} chainCode={} updatedBy={}", designCode, chainCode, updatedBy);
        return true;
    }

    public boolean unbind(String designCode, String chainCode, String updatedBy) {
        int updated = jdbc.update("DELETE FROM zf_design_binding WHERE design_code=? AND chain_code=?",
                designCode, chainCode);
        if (updated == 0) return false;
        // 更新链状态
        String now = LocalDateTime.now().format(DTF);
        jdbc.update("UPDATE zf_chain SET updated_by=?, updated_at=? WHERE code=? AND tenant_id=?",
                updatedBy != null ? updatedBy : "", now, chainCode, tenantId);
        log.info("设计解绑成功 designCode={} chainCode={} updatedBy={}", designCode, chainCode, updatedBy);
        return true;
    }

    public void deleteBindingsByChain(String chainCode) {
        jdbc.update("DELETE FROM zf_design_binding WHERE chain_code = ?", chainCode);
    }
}
