package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.purchase.dto.query.PurInstockQuery;
import com.erp.purchase.dto.vo.PurInstockVO;
import com.erp.purchase.entity.PurInstock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购入库单 Mapper。
 */
public interface PurInstockMapper extends BaseMapper<PurInstock> {

    /**
     * 分页查询入库单（JOIN 供应商/仓库/采购订单带出名称）。
     */
    IPage<PurInstockVO> selectInstockPage(Page<PurInstockVO> page, @Param("q") PurInstockQuery q);

    /**
     * 审核：0草稿 -> 10已审核。条件 UPDATE 防并发重复审核。
     *
     * @param id      入库单 ID
     * @param auditBy 审核人（认证接入前传 0=系统）
     * @return 受影响行数（0=非草稿状态，不可审核）
     */
    @Update("UPDATE pur_instock SET status = 10, audit_by = #{auditBy}, audit_time = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status = 0")
    int audit(@Param("id") Long id, @Param("auditBy") Long auditBy);

    /**
     * 供应商对账：统计某供应商指定月份（[begin, end) 区间）已审核入库金额合计。
     */
    @Select("""
            SELECT COALESCE(SUM(total_amount), 0) FROM pur_instock
            WHERE supplier_id = #{supplierId} AND status = 10 AND deleted = 0
              AND instock_date >= #{begin} AND instock_date < #{end}
            """)
    BigDecimal sumAuditedAmount(@Param("supplierId") Long supplierId,
                                @Param("begin") LocalDate begin, @Param("end") LocalDate end);

    /**
     * 供应商对账：统计某供应商指定月份已审核入库单数。
     */
    @Select("""
            SELECT COUNT(*) FROM pur_instock
            WHERE supplier_id = #{supplierId} AND status = 10 AND deleted = 0
              AND instock_date >= #{begin} AND instock_date < #{end}
            """)
    long countAudited(@Param("supplierId") Long supplierId,
                      @Param("begin") LocalDate begin, @Param("end") LocalDate end);
}
