package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.purchase.dto.vo.PurInstockVO;
import com.erp.purchase.entity.PurInstockDetail;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 采购入库单明细 Mapper。
 */
public interface PurInstockDetailMapper extends BaseMapper<PurInstockDetail> {

    /**
     * 查询入库明细（JOIN base_material），按 id 升序。
     */
    @Select("""
            SELECT d.id, d.order_detail_id, d.material_id, d.batch_no, m.code AS material_code, m.name AS material_name,
                   m.spec, u.name AS unit, d.qty, d.price, d.tax_rate, d.amount, d.returned_qty, d.remark
            FROM pur_instock_detail d
            LEFT JOIN base_material m ON m.id = d.material_id AND m.deleted = 0
            LEFT JOIN base_unit u ON u.id = m.unit_id AND u.deleted = 0
            WHERE d.instock_id = #{instockId} AND d.deleted = 0
            ORDER BY d.id
            """)
    List<PurInstockVO.PurInstockDetailVO> selectDetailsWithMaterial(@Param("instockId") Long instockId);

    /**
     * 退货审核时累加本明细已退数量。
     *
     * @return 受影响行数（0=明细不存在或已删，上层事务回滚）
     */
    @Update("UPDATE pur_instock_detail SET returned_qty = returned_qty + #{qty}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int addReturnedQty(@Param("id") Long id, @Param("qty") java.math.BigDecimal qty);

    /**
     * 带上限的已退数量回写（退货审核专用）：
     * {@code returned_qty + qty <= qty(入库数量)} 不满足则影响 0 行，
     * 防御"多张草稿退货单累计超退"与并发审核竞态，事务整体回滚。
     */
    @Update("UPDATE pur_instock_detail SET returned_qty = returned_qty + #{qty}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND returned_qty + #{qty} <= qty")
    int addReturnedQtyGuarded(@Param("id") Long id, @Param("qty") java.math.BigDecimal qty);

    /**
     * 汇总某订单明细"在途"的入库数量：同一订单明细下所有【草稿】入库单的数量之和
     * （排除当前正在编辑的单据）。订单可入库量的计算必须扣除草稿在途，
     * 否则多张草稿入库单会累计超收（received_qty 只在审核时回写）。
     *
     * @param orderDetailId   订单明细 ID
     * @param excludeInstockId 排除的入库单 ID（修改场景排除自身；新增传 0）
     * @return 草稿在途数量（无则 0）
     */
    @Select("""
            SELECT COALESCE(SUM(d.qty), 0)
            FROM pur_instock_detail d
            JOIN pur_instock i ON i.id = d.instock_id AND i.deleted = 0 AND i.status = 0
            WHERE d.order_detail_id = #{orderDetailId} AND d.deleted = 0
              AND d.instock_id != #{excludeInstockId}
            """)
    java.math.BigDecimal sumDraftPendingQty(@Param("orderDetailId") Long orderDetailId,
                                            @Param("excludeInstockId") Long excludeInstockId);
}
