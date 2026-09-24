package com.erp.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.qc.dto.query.QcInspectQuery;
import com.erp.qc.dto.vo.QcInspectVO;
import com.erp.qc.dto.vo.QcStatisticsVO;
import com.erp.qc.entity.QcInspect;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 检验单 Mapper。
 */
public interface QcInspectMapper extends BaseMapper<QcInspect> {

    /**
     * 分页查询检验单（JOIN 物料/单位/供应商/模板带出名称）。
     */
    IPage<QcInspectVO> selectInspectPage(Page<QcInspectVO> page, @Param("q") QcInspectQuery q);

    /**
     * 提交检验：0草稿 -> 10已检验，一次性回填合格数/不合格数/结论/检验人/检验日期。
     * 条件 UPDATE 防并发重复提交（0 行=非草稿，尚未写任何明细实检值）。
     *
     * @param id              检验单 ID
     * @param qualifiedQty    合格数量
     * @param unqualifiedQty  不合格数量
     * @param conclusion      检验结论（1/2/3）
     * @param inspectorName   检验人姓名
     * @param inspectDate     检验日期
     * @return 受影响行数（0=非草稿状态，不可提交）
     */
    @Update("UPDATE qc_inspect SET status = 10, qualified_qty = #{qualifiedQty}, "
            + "unqualified_qty = #{unqualifiedQty}, conclusion = #{conclusion}, "
            + "inspector_name = #{inspectorName}, inspect_date = #{inspectDate}, "
            + "submit_time = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status = 0")
    int submit(@Param("id") Long id,
               @Param("qualifiedQty") BigDecimal qualifiedQty,
               @Param("unqualifiedQty") BigDecimal unqualifiedQty,
               @Param("conclusion") Integer conclusion,
               @Param("inspectorName") String inspectorName,
               @Param("inspectDate") LocalDate inspectDate);

    /**
     * 卡点：统计来源单下未完成（草稿）检验单数。
     */
    @Select("SELECT COUNT(*) FROM qc_inspect "
            + "WHERE source_type = #{bizType} AND source_id = #{bizId} AND status = 0 AND deleted = 0")
    long countPendingBySource(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /**
     * 卡点：来源单下未完成（草稿）检验单列表（展示用）。
     */
    @Select("SELECT i.id, i.inspect_no, i.status FROM qc_inspect i "
            + "WHERE i.source_type = #{bizType} AND i.source_id = #{bizId} AND i.status = 0 AND i.deleted = 0 "
            + "ORDER BY i.id")
    List<QcStatisticsVO.PendingInspectVO> selectPendingBySource(@Param("bizType") String bizType,
                                                                @Param("bizId") Long bizId);

    /**
     * 统计：按物料汇总已检验单的抽检/合格数量（区间 [from, to]，按检验日期）。
     */
    @Select("""
            SELECT i.material_id            AS materialId,
                   m.code                   AS materialCode,
                   m.name                   AS materialName,
                   SUM(i.sample_qty)        AS sampleQty,
                   SUM(i.qualified_qty)     AS qualifiedQty
            FROM qc_inspect i
            LEFT JOIN base_material m ON m.id = i.material_id AND m.deleted = 0
            WHERE i.status = 10 AND i.deleted = 0
              AND i.inspect_date >= #{from} AND i.inspect_date <= #{to}
            GROUP BY i.material_id, m.code, m.name
            ORDER BY SUM(i.sample_qty) DESC
            """)
    List<QcStatisticsVO.MaterialStatVO> selectMaterialStats(@Param("from") LocalDate from,
                                                            @Param("to") LocalDate to);

    /**
     * 统计：按供应商汇总已检验单（仅带供应商的 IQC 口径）的抽检/合格数量。
     */
    @Select("""
            SELECT i.supplier_id            AS supplierId,
                   s.name                   AS supplierName,
                   SUM(i.sample_qty)        AS sampleQty,
                   SUM(i.qualified_qty)     AS qualifiedQty
            FROM qc_inspect i
            LEFT JOIN base_supplier s ON s.id = i.supplier_id AND s.deleted = 0
            WHERE i.status = 10 AND i.deleted = 0 AND i.supplier_id IS NOT NULL
              AND i.inspect_date >= #{from} AND i.inspect_date <= #{to}
            GROUP BY i.supplier_id, s.name
            ORDER BY SUM(i.sample_qty) DESC
            """)
    List<QcStatisticsVO.SupplierStatVO> selectSupplierStats(@Param("from") LocalDate from,
                                                            @Param("to") LocalDate to);

    /**
     * 统计：按检验日期汇总检验单量与抽检数量（趋势）。
     */
    @Select("""
            SELECT inspect_date AS date, COUNT(*) AS count, SUM(sample_qty) AS sampleQty
            FROM qc_inspect
            WHERE status = 10 AND deleted = 0
              AND inspect_date >= #{from} AND inspect_date <= #{to}
            GROUP BY inspect_date
            ORDER BY inspect_date
            """)
    List<QcStatisticsVO.TrendPointVO> selectTrend(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
