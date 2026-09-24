package com.erp.basedata.mapper;

import com.erp.basedata.dto.query.PriceSourceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采购价格来源扫描 Mapper（V131 扫描器专用，只读）。
 * <p>
 * 按「约定表直查」模式读采购模块表（pur_order / pur_instock / pur_rfq），
 * 不 import 采购模块代码，与销售模块读 base_* 的约定一致；
 * NOT EXISTS 对物理表判断（含已删除流水），删掉的流水不会被重新沉淀。
 * 租户条件由租户插件自动追加。
 */
@Mapper
public interface PriceSourceMapper {

    /**
     * 扫已审核（10/20/30）未沉淀的采购订单明细行。
     *
     * @param limit 单轮上限
     * @return 来源行（含税价 + 税率，沉淀时换算不含税）
     */
    @Select("""
            SELECT o.order_no AS sourceNo, o.supplier_id AS supplierId, o.order_date AS priceDate,
                   d.id AS sourceDetailId, d.material_id AS materialId,
                   d.price AS taxPrice, d.tax_rate AS taxRate
            FROM pur_order o
            JOIN pur_order_detail d ON d.order_id = o.id AND d.deleted = 0
            WHERE o.deleted = 0 AND o.status IN (10, 20, 30) AND o.supplier_id IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM pur_price_record p
                              WHERE p.source_type = 'ORDER' AND p.source_detail_id = d.id)
            ORDER BY o.id DESC
            LIMIT #{limit}
            """)
    List<PriceSourceRow> scanOrderRows(@Param("limit") int limit);

    /**
     * 扫已审核（status=10）未沉淀的采购入库明细行。
     *
     * @param limit 单轮上限
     * @return 来源行（入库价可能高于订单价，按实结换算不含税）
     */
    @Select("""
            SELECT i.instock_no AS sourceNo, i.supplier_id AS supplierId, i.instock_date AS priceDate,
                   d.id AS sourceDetailId, d.material_id AS materialId,
                   d.price AS taxPrice, d.tax_rate AS taxRate
            FROM pur_instock i
            JOIN pur_instock_detail d ON d.instock_id = i.id AND d.deleted = 0
            WHERE i.deleted = 0 AND i.status = 10
              AND NOT EXISTS (SELECT 1 FROM pur_price_record p
                              WHERE p.source_type = 'INSTOCK' AND p.source_detail_id = d.id)
            ORDER BY i.id DESC
            LIMIT #{limit}
            """)
    List<PriceSourceRow> scanInstockRows(@Param("limit") int limit);

    /**
     * 扫已定标（status=10）未沉淀的询比价明细行（定标价视为不含税裸价）。
     *
     * @param limit 单轮上限
     * @return 来源行（taxPrice=award_price，taxRate 为 NULL 即不换算）
     */
    @Select("""
            SELECT r.rfq_no AS sourceNo, it.award_supplier_id AS supplierId,
                   COALESCE(DATE(r.award_time), CURDATE()) AS priceDate,
                   it.id AS sourceDetailId, it.material_id AS materialId,
                   it.award_price AS taxPrice, NULL AS taxRate
            FROM pur_rfq r
            JOIN pur_rfq_item it ON it.rfq_id = r.id AND it.deleted = 0
            WHERE r.deleted = 0 AND r.status = 10
              AND it.award_supplier_id IS NOT NULL AND it.award_price IS NOT NULL
              AND NOT EXISTS (SELECT 1 FROM pur_price_record p
                              WHERE p.source_type = 'RFQ' AND p.source_detail_id = it.id)
            ORDER BY r.id DESC
            LIMIT #{limit}
            """)
    List<PriceSourceRow> scanRfqRows(@Param("limit") int limit);
}
