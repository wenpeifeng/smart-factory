package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.purchase.dto.query.PurReturnQuery;
import com.erp.purchase.dto.vo.PurReturnVO;
import com.erp.purchase.entity.PurReturn;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 采购退货单 Mapper。
 */
public interface PurReturnMapper extends BaseMapper<PurReturn> {

    /**
     * 分页查询退货单（JOIN 供应商/仓库/入库单带出名称）。
     */
    IPage<PurReturnVO> selectReturnPage(Page<PurReturnVO> page, @Param("q") PurReturnQuery q);

    /**
     * 审核：0草稿 -> 10已审核。条件 UPDATE 防并发重复审核。
     */
    @Update("UPDATE pur_return SET status = 10, audit_by = #{auditBy}, audit_time = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND status = 0")
    int audit(@Param("id") Long id, @Param("auditBy") Long auditBy);

    /**
     * 供应商对账：统计某供应商指定月份（[begin, end) 区间）已审核退货金额合计。
     */
    @Select("""
            SELECT COALESCE(SUM(total_amount), 0) FROM pur_return
            WHERE supplier_id = #{supplierId} AND status = 10 AND deleted = 0
              AND return_date >= #{begin} AND return_date < #{end}
            """)
    BigDecimal sumAuditedAmount(@Param("supplierId") Long supplierId,
                                @Param("begin") LocalDate begin, @Param("end") LocalDate end);

    /**
     * 供应商对账：统计某供应商指定月份已审核退货单数。
     */
    @Select("""
            SELECT COUNT(*) FROM pur_return
            WHERE supplier_id = #{supplierId} AND status = 10 AND deleted = 0
              AND return_date >= #{begin} AND return_date < #{end}
            """)
    long countAudited(@Param("supplierId") Long supplierId,
                      @Param("begin") LocalDate begin, @Param("end") LocalDate end);
}
