package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.purchase.dto.vo.PurRfqVO;
import com.erp.purchase.entity.PurRfqItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 询比价明细 Mapper。
 */
public interface PurRfqItemMapper extends BaseMapper<PurRfqItem> {

    /**
     * 查询询价明细（JOIN base_material 带出物料编码/名称/规格/单位），按 id 升序。
     *
     * @param rfqId 询比价单 ID
     * @return 明细 VO 列表
     */
    @Select("""
            SELECT i.id, i.requisition_item_id, i.material_id, m.code AS material_code,
                   m.name AS material_name, m.spec, u.name AS unit, i.qty,
                   i.award_supplier_id, i.award_price, i.award_lead_days, i.pur_order_id, i.remark
            FROM pur_rfq_item i
            LEFT JOIN base_material m ON m.id = i.material_id AND m.deleted = 0
            LEFT JOIN base_unit u ON u.id = m.unit_id AND u.deleted = 0
            WHERE i.rfq_id = #{rfqId} AND i.deleted = 0
            ORDER BY i.id
            """)
    List<PurRfqVO.ItemVO> selectItemsWithMaterial(@Param("rfqId") Long rfqId);

    /**
     * 下推采购订单后回写订单 ID（明细的「已下推」判定依据）。
     *
     * @param id     询比价明细 ID
     * @param orderId 生成的采购订单 ID
     * @return 受影响行数
     */
    @Update("UPDATE pur_rfq_item SET pur_order_id = #{orderId}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int bindOrderId(@Param("id") Long id, @Param("orderId") Long orderId);
}
