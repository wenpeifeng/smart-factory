package com.erp.basedata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.erp.basedata.dto.vo.PriceCompareRowVO;
import com.erp.basedata.entity.PurPriceRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 采购价格流水 Mapper。
 * <p>
 * compare 聚合（多供应商同物料最低/最高/均价/最近价）用手写 SQL；
 * 其余 CRUD/条件查询走 MyBatis-Plus。租户条件由租户插件自动追加。
 */
@Mapper
public interface PurPriceRecordMapper extends BaseMapper<PurPriceRecord> {

    /**
     * 多供应商同物料价格对比聚合（未删除数据；按最近价升序，最低价排前）。
     *
     * @param materialId 物料 ID
     * @return 供应商维度聚合行（次数/最低/最高/均价/最近价/最近日期）
     */
    @Select("""
            SELECT r.supplier_id          AS supplierId,
                   COUNT(*)               AS times,
                   MIN(r.price)           AS minPrice,
                   MAX(r.price)           AS maxPrice,
                   ROUND(AVG(r.price), 4) AS avgPrice,
                   (SELECT p.price FROM pur_price_record p
                     WHERE p.material_id = r.material_id AND p.supplier_id = r.supplier_id AND p.deleted = 0
                     ORDER BY p.price_date DESC, p.id DESC LIMIT 1) AS lastPrice,
                   (SELECT p.price_date FROM pur_price_record p
                     WHERE p.material_id = r.material_id AND p.supplier_id = r.supplier_id AND p.deleted = 0
                     ORDER BY p.price_date DESC, p.id DESC LIMIT 1) AS lastDate
            FROM pur_price_record r
            WHERE r.material_id = #{materialId} AND r.deleted = 0
            GROUP BY r.supplier_id
            ORDER BY lastPrice ASC
            """)
    List<PriceCompareRowVO> compareByMaterial(@Param("materialId") Long materialId);
}
