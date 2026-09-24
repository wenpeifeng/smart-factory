package com.erp.sales.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.sales.dto.vo.CustomerBriefVO;
import com.erp.sales.dto.vo.SalDailyStatVO;
import com.erp.sales.dto.vo.SalOrderDetailVO;
import com.erp.sales.dto.vo.SalOrderVO;
import com.erp.sales.dto.vo.SalOutstockDetailVO;
import com.erp.sales.dto.vo.SalOutstockVO;
import com.erp.sales.dto.vo.SalRankVO;
import com.erp.sales.dto.vo.SalReturnDetailVO;
import com.erp.sales.dto.vo.SalReturnVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售模块自定义 SQL Mapper（XML：resources/mapper/sales/SalesBizMapper.xml）。
 * <p>
 * 职责（MyBatis-Plus 通用 Mapper 覆盖不了的部分）：
 * <ul>
 *   <li>单据分页/详情：JOIN base_customer / base_warehouse / base_material 带出名称；</li>
 *   <li>跨模块表读写（并行协作约定，只按约定结构写 SQL，不 import 对方代码）：
 *       信用校验读 base_customer + fin_receivable；审核写 inv_stock / inv_movement /
 *       fin_receivable；</li>
 *   <li>单据状态机条件更新（防并发跳跃流转）与订单出库数量回写；</li>
 *   <li>销售统计（按日汇总 / 物料·客户排行）。</li>
 * </ul>
 */
@Mapper
public interface SalesBizMapper {

    // ==================== 销售订单 ====================

    /**
     * 分页查询销售订单（关联客户/仓库名称；keyword 匹配订单号或客户名称）。
     *
     * @param page    分页参数（MP 分页拦截器自动拼 LIMIT）
     * @param keyword 关键词（可空）
     * @param status  状态（可空）
     * @return 订单 VO 分页
     */
    Page<SalOrderVO> selectOrderPage(Page<SalOrderVO> page,
                                     @Param("keyword") String keyword,
                                     @Param("status") Integer status);

    /**
     * 查询单个订单表头（含客户/仓库名称）。
     *
     * @param id 订单 ID
     * @return 订单 VO，不存在返回 null
     */
    SalOrderVO selectOrder(@Param("id") Long id);

    /**
     * 查询订单明细行（关联物料编码/名称/规格，含剩余可出数量）。
     *
     * @param orderId 订单 ID
     * @return 明细行列表（按行 ID 升序）
     */
    List<SalOrderDetailVO> selectOrderDetails(@Param("orderId") Long orderId);

    /**
     * 订单审核状态流转：0草稿 → 10已审核（条件更新，返回 0 表示状态已被并发变更）。
     *
     * @param id       订单 ID
     * @param operator 审核人 ID（SecurityUtils.getCurrentUserId()，未登录上下文为 0=系统）
     */
    int auditOrder(@Param("id") Long id, @Param("operator") Long operator);

    /**
     * 订单关闭状态流转：10已审核/20部分出库 → 90已关闭；
     * 已发起过审批流的草稿（wf_started=1，被驳回或审批中）也允许关闭作废
     * （审批流三段式约定"驳回后允许关闭单据"）。
     *
     * @param id       订单 ID
     * @param operator 关闭人 ID
     */
    int closeOrder(@Param("id") Long id, @Param("operator") Long operator);

    /**
     * 出库审核后回写订单行出库数量（带剩余数量校验，防并发超发）。
     *
     * @param orderDetailId 订单行 ID
     * @param qty           本次出库数量
     * @return 影响行数；0 表示超出剩余数量
     */
    int addOrderOutQty(@Param("orderDetailId") Long orderDetailId, @Param("qty") BigDecimal qty);

    /**
     * 刷新订单状态为"已完成"（全部行出库数量到位时生效）。
     */
    int refreshOrderFinished(@Param("orderId") Long orderId);

    /**
     * 刷新订单状态为"部分出库"（存在出库记录但未出完时生效）。
     */
    int refreshOrderPartOut(@Param("orderId") Long orderId);

    // ==================== 销售出库单 ====================

    /**
     * 分页查询销售出库单（关联客户/仓库名称）。
     */
    Page<SalOutstockVO> selectOutstockPage(Page<SalOutstockVO> page,
                                           @Param("keyword") String keyword,
                                           @Param("status") Integer status);

    /**
     * 查询单个出库单表头。
     */
    SalOutstockVO selectOutstock(@Param("id") Long id);

    /**
     * 查询出库单明细行（关联物料信息，含剩余可退数量）。
     */
    List<SalOutstockDetailVO> selectOutstockDetails(@Param("outId") Long outId);

    /**
     * 出库单审核状态流转：0草稿 → 10已审核。
     *
     * @param id       出库单 ID
     * @param operator 审核人 ID（SecurityUtils.getCurrentUserId()）
     */
    int auditOutstock(@Param("id") Long id, @Param("operator") Long operator);

    /**
     * 退货审核后回写出库行已退数量（带可退数量校验，防并发超退）。
     *
     * @return 影响行数；0 表示超出可退数量
     */
    int addOutstockReturnedQty(@Param("outDetailId") Long outDetailId, @Param("qty") BigDecimal qty);

    // ==================== 销售退货单 ====================

    /**
     * 分页查询销售退货单（关联客户/仓库名称）。
     */
    Page<SalReturnVO> selectReturnPage(Page<SalReturnVO> page,
                                       @Param("keyword") String keyword,
                                       @Param("status") Integer status);

    /**
     * 查询单个退货单表头。
     */
    SalReturnVO selectReturn(@Param("id") Long id);

    /**
     * 查询退货单明细行（关联物料信息）。
     */
    List<SalReturnDetailVO> selectReturnDetails(@Param("returnId") Long returnId);

    /**
     * 退货单审核状态流转：0草稿 → 10已审核。
     *
     * @param id       退货单 ID
     * @param operator 审核人 ID（SecurityUtils.getCurrentUserId()）
     */
    int auditReturn(@Param("id") Long id, @Param("operator") Long operator);

    // ==================== 跨模块表读写（并行协作约定结构） ====================
    // 说明：inv_stock / inv_movement 的写入已统一切换为 erp-inventory 的
    // StockService（stockIn/stockOut，批次感知 + FIFO 策略），本 Mapper 不再
    // 保留对库存表的任何写 SQL；fin_receivable 仍按约定结构读写。

    /**
     * 只读客户简要信息（base_customer），用于存在性校验与信用额度读取。
     *
     * @param id 客户 ID
     * @return 客户简要信息；不存在或已删除返回 null
     */
    CustomerBriefVO selectCustomerBrief(@Param("id") Long id);

    /**
     * 只读仓库名称（base_warehouse），用于存在性校验。
     *
     * @param id 仓库 ID
     * @return 仓库名称；不存在或已删除返回 null
     */
    String selectWarehouseName(@Param("id") Long id);

    /**
     * 汇总客户未收回应收余额：SUM(amount - settled_amount)。
     * <p>
     * 只按约定列计算，不依赖 fin_receivable.status 的取值语义；
     * 红字应收（负金额）自然冲减余额。
     *
     * @param customerId 客户 ID
     * @return 未收回应收余额（无记录返回 0）
     */
    BigDecimal sumUnsettledReceivable(@Param("customerId") Long customerId);

    /**
     * 生成应收记录（fin_receivable）。出库审核写正金额（蓝字），
     * 退货审核写负金额（红字冲减）。status/bizDate 按 V501 约定传值。
     *
     * @param customerId 客户 ID
     * @param bizNo      来源单据号（出库单号/退货单号）
     * @param bizDate    业务日期（出库/退货日期）
     * @param amount     应收金额（正蓝字 / 负红字）
     * @param status     应收状态（如 UNSETTLED）
     * @param remark     备注
     * @return 影响行数
     */
    int insertReceivable(@Param("customerId") Long customerId,
                         @Param("bizNo") String bizNo,
                         @Param("bizDate") LocalDate bizDate,
                         @Param("amount") BigDecimal amount,
                         @Param("status") String status,
                         @Param("remark") String remark);

    // ==================== 销售统计 ====================

    /**
     * 按日汇总销售额/成本/毛利（仅"已审核"出库单）。
     * 成本口径（V2101 升级）：明细行 actual_cost（审核时 CostService 移动加权实际成本）
     * 优先；历史出库行 actual_cost 为 NULL 时回退 base_material.ref_cost × 数量估算。
     *
     * @param from 开始日期（含）
     * @param to   结束日期（含）
     * @return 按日期升序的统计行（无单据的日期不出现）
     */
    List<SalDailyStatVO> selectDailyStats(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 物料销售排行 TOP10（按销售额降序）。
     */
    List<SalRankVO> selectMaterialRank(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /**
     * 客户销售排行 TOP10（按销售额降序）。
     */
    List<SalRankVO> selectCustomerRank(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
