package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.purchase.dto.vo.PurOrderVO;
import com.erp.purchase.entity.PurOrderDetail;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 采购订单明细 Mapper。
 */
public interface PurOrderDetailMapper extends BaseMapper<PurOrderDetail> {

    /**
     * 查询订单明细（JOIN base_material 带出物料编码/名称/规格/单位），按 id 升序。
     *
     * @param orderId 订单 ID
     * @return 明细 VO 列表
     */
    @Select("""
            SELECT d.id, d.material_id, m.code AS material_code, m.name AS material_name,
                   m.spec, u.name AS unit, d.qty, d.price, d.tax_rate, d.amount,
                   d.delivery_date, d.received_qty, d.remark
            FROM pur_order_detail d
            LEFT JOIN base_material m ON m.id = d.material_id AND m.deleted = 0
            LEFT JOIN base_unit u ON u.id = m.unit_id AND u.deleted = 0
            WHERE d.order_id = #{orderId} AND d.deleted = 0
            ORDER BY d.id
            """)
    List<PurOrderVO.PurOrderDetailVO> selectDetailsWithMaterial(@Param("orderId") Long orderId);

    /**
     * 累计入库数量回写：入库审核传正数、退货审核传负数。
     * 库存行锁由上层事务保证一致性；返回受影响行数用于防御明细被并发删除。
     *
     * @param id  订单明细 ID
     * @param qty 增量（正=入库 负=退货冲减）
     * @return 受影响行数
     */
    @Update("UPDATE pur_order_detail SET received_qty = received_qty + #{qty}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int addReceivedQty(@Param("id") Long id, @Param("qty") java.math.BigDecimal qty);

    /**
     * 带超收上限的累计入库回写（入库审核专用）：
     * {@code received_qty + qty <= qty(订单数量)} 不满足则影响 0 行，
     * 防御"多张草稿入库单累计超收"以及并发审核竞态（数据库层兜底，事务回滚）。
     * 退货审核不适用（负增量），仍用 {@link #addReceivedQty}。
     */
    @Update("UPDATE pur_order_detail SET received_qty = received_qty + #{qty}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND received_qty + #{qty} <= qty")
    int addReceivedQtyGuarded(@Param("id") Long id, @Param("qty") java.math.BigDecimal qty);
}
