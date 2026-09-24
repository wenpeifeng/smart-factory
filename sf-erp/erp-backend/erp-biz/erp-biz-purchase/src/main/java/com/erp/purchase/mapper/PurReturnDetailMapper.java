package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.purchase.dto.vo.PurReturnVO;
import com.erp.purchase.entity.PurReturnDetail;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采购退货单明细 Mapper。
 */
public interface PurReturnDetailMapper extends BaseMapper<PurReturnDetail> {

    /**
     * 查询退货明细（JOIN base_material），按 id 升序。
     */
    @Select("""
            SELECT d.id, d.instock_detail_id, d.material_id, m.code AS material_code, m.name AS material_name,
                   m.spec, u.name AS unit, d.qty, d.price, d.tax_rate, d.amount, d.remark
            FROM pur_return_detail d
            LEFT JOIN base_material m ON m.id = d.material_id AND m.deleted = 0
            LEFT JOIN base_unit u ON u.id = m.unit_id AND u.deleted = 0
            WHERE d.return_id = #{returnId} AND d.deleted = 0
            ORDER BY d.id
            """)
    List<PurReturnVO.PurReturnDetailVO> selectDetailsWithMaterial(@Param("returnId") Long returnId);

    /**
     * 汇总某入库明细"在途"的退货数量：该入库明细下所有【草稿】退货单的数量之和
     * （排除当前正在编辑的单据）。可退数量的计算必须扣除草稿在途，
     * 否则多张草稿退货单会累计超退（returned_qty 只在审核时回写）。
     *
     * @param instockDetailId 入库明细 ID
     * @param excludeReturnId 排除的退货单 ID（修改场景排除自身；新增传 0）
     * @return 草稿在途退货数量（无则 0）
     */
    @Select("""
            SELECT COALESCE(SUM(d.qty), 0)
            FROM pur_return_detail d
            JOIN pur_return r ON r.id = d.return_id AND r.deleted = 0 AND r.status = 0
            WHERE d.instock_detail_id = #{instockDetailId} AND d.deleted = 0
              AND d.return_id != #{excludeReturnId}
            """)
    java.math.BigDecimal sumDraftPendingQty(@Param("instockDetailId") Long instockDetailId,
                                            @Param("excludeReturnId") Long excludeReturnId);
}
