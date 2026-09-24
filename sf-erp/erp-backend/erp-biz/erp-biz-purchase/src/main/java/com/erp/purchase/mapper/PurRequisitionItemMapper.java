package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.purchase.dto.vo.PurRequisitionVO;
import com.erp.purchase.entity.PurRequisitionItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * 请购单明细 Mapper。
 */
public interface PurRequisitionItemMapper extends BaseMapper<PurRequisitionItem> {

    /**
     * 查询请购明细（JOIN base_material 带出物料编码/名称/规格/单位），按 id 升序。
     *
     * @param requisitionId 请购单 ID
     * @return 明细 VO 列表
     */
    @Select("""
            SELECT i.id, i.material_id, m.code AS material_code, m.name AS material_name,
                   m.spec, u.name AS unit, i.qty, i.ordered_qty, i.require_date, i.remark
            FROM pur_requisition_item i
            LEFT JOIN base_material m ON m.id = i.material_id AND m.deleted = 0
            LEFT JOIN base_unit u ON u.id = m.unit_id AND u.deleted = 0
            WHERE i.requisition_id = #{requisitionId} AND i.deleted = 0
            ORDER BY i.id
            """)
    List<PurRequisitionVO.ItemVO> selectItemsWithMaterial(@Param("requisitionId") Long requisitionId);

    /**
     * 【超转守卫·数据库层兜底】已转采购数量回写（询比价定标事务调用）：
     * {@code ordered_qty + delta <= qty} 不满足则影响 0 行，上层整体回滚，
     * 防御多张 RFQ 并发定标累计超过请购数量的竞态。
     *
     * @param id    请购明细 ID
     * @param delta 定标数量（恒为正）
     * @return 受影响行数（0=超出请购数量，回滚）
     */
    @Update("UPDATE pur_requisition_item SET ordered_qty = ordered_qty + #{delta}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0 AND ordered_qty + #{delta} <= qty")
    int addOrderedQtyGuarded(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
