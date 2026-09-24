package com.erp.sales.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.sales.dto.vo.MaterialCostVO;
import com.erp.sales.dto.vo.OutstockLineVO;
import com.erp.sales.dto.vo.PendingStockVO;
import com.erp.sales.dto.vo.SalNoticeItemVO;
import com.erp.sales.dto.vo.SalNoticeVO;
import com.erp.sales.dto.vo.SalOutstockVO;
import com.erp.sales.dto.vo.SalQuotationItemVO;
import com.erp.sales.dto.vo.SalQuotationVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 销售前端链路（报价单 / 发货通知单）自定义 SQL Mapper
 * （XML：resources/mapper/sales/SalesLinkMapper.xml）。
 * <p>
 * 职责（MyBatis-Plus 通用 Mapper 覆盖不了的部分）：
 * <ul>
 *   <li>单据分页/详情：JOIN base_customer / base_warehouse / base_material 带出名称；</li>
 *   <li>报价单"已过期"有效状态的动态判定（status=10 且 valid_until 早于当天 → 30）；</li>
 *   <li>单据状态机条件更新（防并发跳跃流转）与核销数量守卫累加；</li>
 *   <li>跨模块只读：base_material（ref_cost 成本快照 / 名称校验）、inv_stock（备货参考）；
 *       出库单据（sal_outstock）按单号读取用于核销匹配。</li>
 * </ul>
 */
@Mapper
public interface SalesLinkMapper {

    // ==================== 报价单 ====================

    /**
     * 分页查询报价单（关联客户名称）。
     * status 传 30 表示查"已过期"（SQL 动态判定），传 10 仅查未过期的已报价。
     *
     * @param page      分页参数
     * @param keyword   关键词（报价单号/客户名称，可空）
     * @param customerId 客户 ID（可空）
     * @param status    有效状态（可空，支持 30）
     * @param validFrom 有效期起（可空）
     * @param validTo   有效期止（可空）
     */
    Page<SalQuotationVO> selectQuotationPage(Page<SalQuotationVO> page,
                                             @Param("keyword") String keyword,
                                             @Param("customerId") Long customerId,
                                             @Param("status") Integer status,
                                             @Param("validFrom") LocalDate validFrom,
                                             @Param("validTo") LocalDate validTo);

    /** 查询单个报价单表头（含动态有效状态 30已过期）。 */
    SalQuotationVO selectQuotation(@Param("id") Long id);

    /** 查询报价明细行（关联物料编码/名称/规格）。 */
    List<SalQuotationItemVO> selectQuotationItems(@Param("quotationId") Long quotationId);

    /** 提交报价：0草稿 → 10已报价（条件更新，0=状态已被并发变更）。 */
    int submitQuotation(@Param("id") Long id, @Param("operator") Long operator);

    /** 作废报价：0草稿/10已报价 → 40已作废（条件更新）。 */
    int voidQuotation(@Param("id") Long id, @Param("operator") Long operator);

    /** 下推回写：10已报价(未过期) → 20已转订单，并记录订单 ID/单号。 */
    int markQuotationOrdered(@Param("id") Long id,
                             @Param("orderId") Long orderId,
                             @Param("orderNo") String orderNo);

    /** 批量只读物料成本（base_material.ref_cost），用于报价明细成本快照。 */
    List<MaterialCostVO> selectMaterialCosts(@Param("ids") List<Long> ids);

    /** 只读物料名称（存在性校验，直发通知行用）。 */
    String selectMaterialName(@Param("id") Long id);

    /** 第一个在用仓库 ID（下推订单未指定仓库时兜底）。 */
    Long selectFirstWarehouseId();

    // ==================== 发货通知单 ====================

    /** 分页查询发货通知单（关联客户/仓库名称，含明细数量汇总）。 */
    Page<SalNoticeVO> selectNoticePage(Page<SalNoticeVO> page,
                                       @Param("keyword") String keyword,
                                       @Param("status") Integer status);

    /** 查询单个通知单表头（含明细数量汇总）。 */
    SalNoticeVO selectNotice(@Param("id") Long id);

    /** 查询通知明细行（关联物料信息，含剩余待出库数量）。 */
    List<SalNoticeItemVO> selectNoticeItems(@Param("noticeId") Long noticeId);

    /** 通知发货：0草稿 → 10已通知（条件更新）。 */
    int notifyNotice(@Param("id") Long id, @Param("operator") Long operator);

    /** 取消通知：0/10 且未发生任何出库 → 30已取消（条件更新）。 */
    int cancelNotice(@Param("id") Long id, @Param("operator") Long operator);

    /** 核销守卫累加：out_qty + qty ≤ qty（0=超出通知量或并发冲突）。 */
    int addNoticeOutQty(@Param("itemId") Long itemId, @Param("qty") BigDecimal qty);

    /** 核销后刷新状态：10已通知 → 20已出库（全部明细核销到位时生效）。 */
    int refreshNoticeOut(@Param("id") Long id);

    /** 记录最近核销人/时间（不参与状态判定，仅审计留痕）。 */
    int markNoticeSettled(@Param("id") Long id, @Param("operator") Long operator);

    /** 汇总某订单明细在全部有效通知单（0/10）中的累计通知数量（编辑时排除自身）。 */
    BigDecimal sumActiveNoticeQty(@Param("orderDetailId") Long orderDetailId,
                                  @Param("excludeNoticeId") Long excludeNoticeId);

    /** 按出库单号读取出库单简要（核销入口；不存在返回 null）。 */
    SalOutstockVO selectOutstockBriefByNo(@Param("outNo") String outNo);

    /** 出库单明细按（订单明细, 物料）分组的数量行（核销匹配用）。 */
    List<OutstockLineVO> selectOutstockLines(@Param("outNo") String outNo);

    // ==================== 备货参考 ====================

    /**
     * 物料 × 仓库维度可用量（SUM inv_stock.qty，跨模块只读约定结构）。
     *
     * @param warehouseId 仓库 ID（可空 = 全部仓库）
     * @param keyword     物料编码/名称模糊（可空）
     */
    List<PendingStockVO> selectPendingStocks(@Param("warehouseId") Long warehouseId,
                                             @Param("keyword") String keyword);
}
