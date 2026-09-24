package com.erp.purchase.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.purchase.entity.PurRfqQuote;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;

/**
 * 询比价报价行 Mapper。
 * <p>
 * 查询走 MP lambda（BaseMapper）。重录报价用<b>物理删除</b>清旧行：
 * uk_quote(rfq_item_id, supplier_id) 是真实唯一索引，逻辑删除后旧行仍占用索引，
 * 同键再插会 Duplicate key，故覆盖场景必须物理删（uk 完整性 > 报价历史留痕）。
 */
public interface PurRfqQuoteMapper extends BaseMapper<PurRfqQuote> {

    /**
     * 物理删除某供应商在本单的全部报价行（重录=覆盖；uk_quote 冲突免疫，见类注释）。
     *
     * @param rfqId      询比价单 ID
     * @param supplierId 供应商 ID
     * @return 删除行数
     */
    @Delete("DELETE FROM pur_rfq_quote WHERE rfq_id = #{rfqId} AND supplier_id = #{supplierId}")
    int physicalDeleteBySupplier(@Param("rfqId") Long rfqId, @Param("supplierId") Long supplierId);
}
