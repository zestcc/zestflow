package com.zestflow.collector.jdbc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zestflow.collector.jdbc.entity.ChainGraphSnapshotPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 链图数据快照 Mapper
 */
@Mapper
public interface ChainGraphSnapshotMapper extends BaseMapper<ChainGraphSnapshotPO> {

    /**
     * 查询指定链的最大版本号（锁定该链的快照行防并发）
     */
    @Select("SELECT MAX(version) FROM chain_graph_snapshot WHERE chain_code = #{chainCode} AND tenant_id = #{tenantId} FOR UPDATE")
    Integer selectMaxVersionForUpdate(@Param("chainCode") String chainCode, @Param("tenantId") Long tenantId);

    /**
     * 软废弃未被引用的旧快照 — 仅废弃无任何执行记录引用的版本
     */
    @Update("UPDATE chain_graph_snapshot s " +
            "LEFT JOIN chain_event e " +
            "  ON e.chain_id = s.chain_code " +
            "  AND e.timestamp >= UNIX_TIMESTAMP(s.created_at) * 1000 " +
            "SET s.STATUS = 0 " +
            "WHERE s.chain_code = #{chainCode} " +
            "  AND s.tenant_id = #{tenantId} " +
            "  AND s.STATUS = 1 " +
            "  AND e.id IS NULL")
    int deprecateUnreferenced(@Param("chainCode") String chainCode, @Param("tenantId") Long tenantId);
}
