package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.purchase.dto.query.PurEstimationQuery;
import com.erp.purchase.dto.vo.PurEstimationSummaryVO;
import com.erp.purchase.dto.vo.PurEstimationVO;
import com.erp.purchase.entity.PurEstimation;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 采购暂估应付单 Mapper（pur_estimation，V231）。
 */
public interface PurEstimationMapper extends BaseMapper<PurEstimation> {

    /**
     * 分页查询暂估单（JOIN 供应商带出名称；keyword 匹配暂估单号/入库单号/发票号）。
     */
    IPage<PurEstimationVO> selectEstimationPage(Page<PurEstimationVO> page, @Param("q") PurEstimationQuery q);

    /**
     * 暂估报表：按暂估期间 + 供应商维度汇总（暂估总额 / 已冲回 / 未冲回余额）。
     * <p>
     * 口径：按 pur_estimation.period（暂估期间 yyyyMM）归属，冲回金额计入所属暂估单的期间，
     * 与"当月暂估"报表语义一致。
     */
    List<PurEstimationSummaryVO.SupplierRow> summaryBySupplier(@Param("period") String period);

    /**
     * 冲回进度条件 UPDATE：累计已冲回 + cover，同步状态/发票号清单。
     * <p>
     * 数据库层兜底：仅未冲完状态（0/20）可冲、且 amount - reversed_amount >= cover
     * （并发冲回不超额），影响 0 行=状态已变更，调用方整体回滚。
     */
    @Update("""
            UPDATE pur_estimation
            SET reversed_amount = reversed_amount + #{cover},
                status          = #{status},
                invoice_no      = #{invoiceNo},
                reversed_invoice_nos = #{reversedInvoiceNos},
                update_time     = NOW()
            WHERE id = #{id} AND deleted = 0
              AND status IN (0, 20)
              AND amount - reversed_amount >= #{cover}
            """)
    int addReversedAmount(@Param("id") Long id, @Param("cover") java.math.BigDecimal cover,
                          @Param("status") int status, @Param("invoiceNo") String invoiceNo,
                          @Param("reversedInvoiceNos") String reversedInvoiceNos);

    /**
     * 扫描器：已审核、尚无暂估、且不存在生效（待认证/已认证）蓝字发票的入库单 ID 清单。
     * <p>
     * monthEnd=true（month-end 汇总模式）只补提已关账月份（入库日期 < 当前月 1 号）；
     * instant 模式补提全部漏单（事件挂点失败/开关期切换等兜底）。
     */
    @Select("""
            SELECT i.id FROM pur_instock i
            WHERE i.status = 10 AND i.deleted = 0
              AND (#{monthEnd} = FALSE OR i.instock_date < #{monthStart})
              AND NOT EXISTS (SELECT 1 FROM pur_estimation e
                              WHERE e.instock_id = i.id AND e.deleted = 0)
              AND NOT EXISTS (SELECT 1 FROM inv_purchase_invoice v
                              WHERE v.biz_type = 'PUR_INSTOCK' AND v.biz_id = i.id
                                AND v.red_flag = 0 AND v.status IN (0, 10) AND v.deleted = 0)
            ORDER BY i.id LIMIT 200
            """)
    List<Long> selectInstockIdsWithoutEstimation(@Param("monthEnd") boolean monthEnd,
                                                 @Param("monthStart") java.time.LocalDate monthStart);

    /**
     * 扫描器：已认证蓝字进项发票（关联入库单存在未冲完的暂估）——冲回候选。
     * 返回发票 id，Service 再逐单加载判断是否已用过。
     */
    @Select("""
            SELECT v.id FROM inv_purchase_invoice v
            WHERE v.biz_type = 'PUR_INSTOCK' AND v.red_flag = 0 AND v.status = 10 AND v.deleted = 0
              AND EXISTS (SELECT 1 FROM pur_estimation e
                          WHERE e.instock_id = v.biz_id AND e.deleted = 0
                            AND e.status IN (0, 20))
            ORDER BY v.id LIMIT 200
            """)
    List<Long> selectAuthedInvoiceIdsForReverse();
}
