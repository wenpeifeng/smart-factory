package com.erp.finance.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 付款预算归属推导 Mapper（finance ↔ budget 接线，只读）。
 * <p>
 * 用途：付款单未显式指定费用归属部门（fin_payment.dept_id 为空）时，按
 * 「供应商 → 最近采购/应付单」的创建人部门（sys_user.dept_id）近似推导部门归属，
 * 推导不出则跳过预算校验（约定见 docs/modules/budget.md §5）。
 * <p>
 * 跨模块只读约定（不 import 对方代码）：
 * <ul>
 *   <li>pur_order（V201）—— 供应商最近一张采购单的创建人；</li>
 *   <li>fin_payable（本模块表）—— 供应商最近一笔应付的创建人（兜底）；</li>
 *   <li>sys_user（V1）—— 创建人所属部门（与预算凭证口径的部门归属约定一致）。</li>
 * </ul>
 * 本 Mapper 只读，禁止出现 INSERT/UPDATE/DELETE。pur_order 表在纯 finance
 * 隔离库可能不存在，由 Service 层捕获 BadSqlGrammarException 降级跳过。
 */
public interface BudgetIntegrationMapper {

    /**
     * 供应商最近一张采购订单的创建人（按 id 倒序取第一张）。
     *
     * @return 无采购单返回 null
     */
    @Select("SELECT create_by FROM pur_order WHERE supplier_id = #{supplierId} AND deleted = 0 "
            + "ORDER BY id DESC LIMIT 1")
    Long selectLatestPurOrderCreator(@Param("supplierId") Long supplierId);

    /**
     * 供应商在本账套最近一笔应付单的创建人（兜底口径，应付多由采购入库审核写入）。
     *
     * @return 无应付返回 null
     */
    @Select("SELECT create_by FROM fin_payable WHERE supplier_id = #{supplierId} "
            + "AND book_id = #{bookId} AND deleted = 0 ORDER BY id DESC LIMIT 1")
    Long selectLatestPayableCreator(@Param("bookId") Long bookId,
                                    @Param("supplierId") Long supplierId);

    /**
     * 用户所属部门（sys_user.dept_id）。
     *
     * @return 用户不存在或未挂部门返回 null
     */
    @Select("SELECT dept_id FROM sys_user WHERE id = #{userId} AND deleted = 0 "
            + "AND dept_id IS NOT NULL")
    Long selectUserDeptId(@Param("userId") Long userId);
}
