package com.erp.basedata.mapper;

import com.erp.basedata.dto.query.ScoreSourceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * 供应商评分来源扫描 Mapper（V141 评分器专用，只读）。
 * <p>
 * 按「约定表直查」模式读采购/QC 模块表（pur_order / pur_instock /
 * pur_instock_detail / pur_return / pur_return_detail / qc_inspect），
 * 不 import 对方代码，与 V131 PriceSourceMapper 同一约定。
 * 租户条件由租户插件自动追加。
 */
@Mapper
public interface SupplierScoreSourceMapper {

    /** 期间已审核订单（10/20/30）：单数 + 交易额 */
    @Select("""
            SELECT o.supplier_id AS supplierId, COUNT(*) AS orderCount, COALESCE(SUM(o.total_amount), 0) AS orderAmount
            FROM pur_order o
            WHERE o.deleted = 0 AND o.status IN (10, 20, 30)
              AND o.supplier_id IS NOT NULL
              AND o.order_date BETWEEN #{start} AND #{end}
            GROUP BY o.supplier_id
            """)
    List<ScoreSourceRow> sumOrders(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 期间已审核入库单：单数 / 有交期单数 / 准时单数（入库日 ≤ 订单最早要求交期）/ 入库数量 */
    @Select("""
            SELECT i.supplier_id AS supplierId,
                   COUNT(*) AS instockCount,
                   SUM(CASE WHEN dd.first_delivery IS NOT NULL THEN 1 ELSE 0 END) AS dueCount,
                   SUM(CASE WHEN dd.first_delivery IS NOT NULL AND i.instock_date <= dd.first_delivery THEN 1 ELSE 0 END) AS ontimeCount,
                   COALESCE(SUM(d.qty), 0) AS instockQty
            FROM pur_instock i
            JOIN pur_instock_detail d ON d.instock_id = i.id AND d.deleted = 0
            LEFT JOIN (SELECT order_id, MIN(delivery_date) AS first_delivery
                       FROM pur_order_detail
                       WHERE deleted = 0 AND delivery_date IS NOT NULL
                       GROUP BY order_id) dd ON dd.order_id = i.order_id
            WHERE i.deleted = 0 AND i.status = 10
              AND i.instock_date BETWEEN #{start} AND #{end}
            GROUP BY i.supplier_id
            """)
    List<ScoreSourceRow> sumInstocks(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 期间已检验 IQC（来源采购入库）：合格/不合格数量 */
    @Select("""
            SELECT q.supplier_id AS supplierId,
                   COALESCE(SUM(q.qualified_qty), 0) AS qualifiedQty,
                   COALESCE(SUM(q.unqualified_qty), 0) AS unqualifiedQty
            FROM qc_inspect q
            WHERE q.deleted = 0 AND q.status = 10
              AND q.source_type = 'pur_instock' AND q.supplier_id IS NOT NULL
              AND q.inspect_date BETWEEN #{start} AND #{end}
            GROUP BY q.supplier_id
            """)
    List<ScoreSourceRow> sumQcInspects(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /** 期间已审核采购退货：退货数量（退货率分子，分母用同期入库数量） */
    @Select("""
            SELECT r.supplier_id AS supplierId, COALESCE(SUM(d.qty), 0) AS returnQty
            FROM pur_return r
            JOIN pur_return_detail d ON d.return_id = r.id AND d.deleted = 0
            WHERE r.deleted = 0 AND r.status = 10
              AND r.return_date BETWEEN #{start} AND #{end}
            GROUP BY r.supplier_id
            """)
    List<ScoreSourceRow> sumReturns(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
