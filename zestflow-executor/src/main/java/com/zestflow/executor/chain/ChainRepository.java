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

    public ChainPO create(String name, String description, String moduleCode, Integer status, String updatedBy) {
        String code = CodeGenerator.generate("CHN");
        String now = LocalDateTime.now().format(DTF);
        String creator = updatedBy != null ? updatedBy : (moduleCode != null ? moduleCode : "");
        jdbc.update("INSERT INTO zf_chain(code, name, description, status, created_by, updated_by, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?)",
                code, name, description, status != null ? status : 1,
                creator, creator, now, now);
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
}
