package com.erp.workflow.mapper;

import com.erp.workflow.dto.vo.CreditWarnRow;
import com.erp.workflow.dto.vo.OrderWarnRow;
import com.erp.workflow.dto.vo.StockWarnRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 工作流跨模块只读引用 Mapper。
 * <p>
 * 按 docs/开发规范.md"跨模块只调约定表结构、不 import 他人代码"的原则，
 * 与采购模块 BasedataMapper 同一做法：@Select 直查他方表（sys_user / sys_role /
 * base_material / inv_stock / base_customer / fin_receivable / mrp_work_order），
 * 仅作 LEFT JOIN/聚合投影，不做任何写入。表结构变更由各 owner 模块文档维护。
 */
public interface WfRefMapper {

    /**
     * 查用户显示姓名（消息/任务展示用）。
     *
     * @param userId 用户 ID
     * @return 昵称；不存在返回 null
     */
    @Select("SELECT nickname FROM sys_user WHERE id = #{userId} AND deleted = 0")
    String selectUserNickname(@Param("userId") Long userId);

    /**
     * 查角色下全部启用用户的 ID（角色节点或签任务的派发范围）。
     *
     * @param roleId 角色 ID
     * @return 用户 ID 列表（角色无启用成员时为空）
     */
    @Select("""
            SELECT ur.user_id
            FROM sys_user_role ur
            JOIN sys_user u ON u.id = ur.user_id AND u.deleted = 0 AND u.status = 1
            WHERE ur.role_id = #{roleId}
              AND ur.deleted = 0
            ORDER BY ur.user_id
            """)
    List<Long> selectEnabledUserIdsByRole(@Param("roleId") Long roleId);

    /**
     * 库存低于安全库存扫描：全仓现存量汇总(base_material.safety_stock vs inv_stock.qty)。
     *
     * @return 安全库存大于 0 且现存量低于安全库存的物料行
     */
    @Select("""
            SELECT m.id          AS materialId,
                   m.code        AS materialCode,
                   m.name        AS materialName,
                   m.safety_stock AS safetyStock,
                   COALESCE(SUM(s.qty), 0) AS totalQty
            FROM base_material m
            LEFT JOIN inv_stock s ON s.material_id = m.id AND s.deleted = 0
            WHERE m.deleted = 0
              AND m.status = 1
              AND m.safety_stock > 0
            GROUP BY m.id, m.code, m.name, m.safety_stock
            HAVING COALESCE(SUM(s.qty), 0) < m.safety_stock
            """)
    List<StockWarnRow> scanSafetyStock();

    /**
     * 客户信用超限扫描：未收回应收合计（fin_receivable 中非 SETTLED 的 amount-settled_amount 之和）
     * 超过 base_customer.credit_limit。口径不含客户期初应收(init_receivable)，仅计系统内单据。
     *
     * @return 信用超限客户行
     */
    @Select("""
            SELECT c.id    AS customerId,
                   c.code  AS customerCode,
                   c.name  AS customerName,
                   c.credit_limit AS creditLimit,
                   COALESCE(SUM(r.amount - r.settled_amount), 0) AS outstandingAmount
            FROM base_customer c
            LEFT JOIN fin_receivable r
                   ON r.customer_id = c.id
                  AND r.deleted = 0
                  AND r.status <> 'SETTLED'
            WHERE c.deleted = 0
              AND c.status = 1
              AND c.credit_limit > 0
            GROUP BY c.id, c.code, c.name, c.credit_limit
            HAVING COALESCE(SUM(r.amount - r.settled_amount), 0) > c.credit_limit
            """)
    List<CreditWarnRow> scanCreditExceeded();

    /**
     * 生产订单计划结束超期扫描：未完工（非 FINISHED/CLOSED）且计划结束日期早于今天。
     *
     * @return 超期生产订单行
     */
    @Select("""
            SELECT id       AS orderId,
                   order_no AS orderNo,
                   status   AS status,
                   plan_end_date AS planEndDate
            FROM mrp_work_order
            WHERE deleted = 0
              AND status IN ('DRAFT', 'RELEASED', 'PRODUCING')
              AND plan_end_date < CURDATE()
            """)
    List<OrderWarnRow> scanOverdueWorkOrders();
}
