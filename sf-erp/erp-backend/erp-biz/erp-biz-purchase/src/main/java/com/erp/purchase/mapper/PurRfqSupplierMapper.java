package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.purchase.dto.vo.PurRfqVO;
import com.erp.purchase.entity.PurRfqSupplier;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 询比价供应商 Mapper。
 */
public interface PurRfqSupplierMapper extends BaseMapper<PurRfqSupplier> {

    /**
     * 查询本单邀请供应商（JOIN base_supplier 带出名称，含历史停用资料），按报价状态/ID 排序。
     *
     * @param rfqId 询比价单 ID
     * @return 供应商 VO 列表
     */
    @Select("""
            SELECT s.id, s.supplier_id, b.name AS supplier_name, s.quote_amount, s.status
            FROM pur_rfq_supplier s
            LEFT JOIN base_supplier b ON b.id = s.supplier_id AND b.deleted = 0
            WHERE s.rfq_id = #{rfqId} AND s.deleted = 0
            ORDER BY s.id
            """)
    List<PurRfqVO.SupplierVO> selectSuppliersWithNames(@Param("rfqId") Long rfqId);

    /**
     * 定标时标记中标供应商行状态 -> 20中标。
     *
     * @param id pur_rfq_supplier 行 ID
     * @return 受影响行数
     */
    @Update("UPDATE pur_rfq_supplier SET status = 20, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int markWon(@Param("id") Long id);

    /**
     * 物理删除邀请行（修改邀请名单移出供应商时用）：
     * uk_rfq_supplier(rfq_id, supplier_id) 是真实唯一索引，逻辑删除后旧行仍占用索引，
     * 后续再邀请同一家会 Duplicate key，故移出场景物理删（RFQ 自身删除仍走逻辑删）。
     *
     * @param rfqId      询比价单 ID
     * @param supplierId 供应商 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM pur_rfq_supplier WHERE rfq_id = #{rfqId} AND supplier_id = #{supplierId}")
    int physicalDeleteBySupplier(@Param("rfqId") Long rfqId, @Param("supplierId") Long supplierId);
}
