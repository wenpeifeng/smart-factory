package com.erp.cost.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.erp.cost.dto.raw.IssueItemRaw;
import com.erp.cost.dto.raw.MovementRaw;
import com.erp.cost.dto.raw.SubjectRaw;
import com.erp.cost.dto.raw.VoucherEntryInsertParam;
import com.erp.cost.dto.raw.VoucherInsertParam;
import com.erp.cost.dto.raw.WageRaw;
import com.erp.cost.dto.raw.WorkOrderRaw;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 成本模块跨模块集成 Mapper —— 只按约定表结构读写，不 import 其他业务模块代码。
 * <p>
 * 约定表清单（列以一期真实 DDL 为准）：
 * <ul>
 *   <li>inv_movement（读）：id, biz_type, biz_id, biz_no, warehouse_id, material_id, qty(正入负出), create_time —— sync 重放数据源</li>
 *   <li>pur_instock_detail（读）：instock_id, material_id, amount —— PUR_IN 计价金额来源</li>
 *   <li>mrp_work_order / mrp_material_issue(_item)（读）：工单与领料归集</li>
 *   <li>hr_piece_wage_detail（读，HR 二期并行开发）：work_order_id, employee_id, amount —— 未建成前由调用方降级</li>
 *   <li>fin_voucher / fin_voucher_entry（写）：月末结账自动生成转账凭证</li>
 *   <li>fin_subject（读）：按编码解析主营业务成本/生产成本/库存商品科目</li>
 *   <li>base_material / base_warehouse（读）：名称回显</li>
 * </ul>
 */
public interface CostIntegrationMapper {

    /** 取 toPeriod（含）之前的全部库存流水（sync 重放数据源，按时间+id 升序） */
    List<MovementRaw> selectMovements(@Param("toPeriod") String toPeriod);

    /** 采购入库单某物料的入库金额合计（PUR_IN 计价金额来源；无明细返回 null） */
    BigDecimal sumPurchaseInAmount(@Param("instockId") Long instockId,
                                   @Param("materialId") Long materialId);

    /** 生产订单分页（带物料回显，keyword 匹配工单号/物料编码/物料名称） */
    IPage<WorkOrderRaw> selectWorkOrderPage(IPage<WorkOrderRaw> page,
                                            @Param("keyword") String keyword,
                                            @Param("status") String status);

    /** 按 ID 精确取生产订单（不存在返回空列表） */
    List<WorkOrderRaw> selectWorkOrderById(@Param("id") Long id);

    /** 按工单 ID 批量取已审核领料明细（汇总到 工单×物料 粒度） */
    List<IssueItemRaw> selectIssueItems(@Param("workOrderIds") List<Long> workOrderIds);

    /** hr_piece_wage_detail 约定表是否存在（HR 模块并行开发，未建成前降级为无工资数据） */
    /** information_schema 探测不走租户过滤（该库无 tenant_id 列） */
    @InterceptorIgnore(tenantLine = "true")
    int countHrWageTable();

    /** 按工单 ID 批量取计件工资（汇总到工单粒度；表必须先经 countHrWageTable 确认存在） */
    List<WageRaw> selectWageTotals(@Param("workOrderIds") List<Long> workOrderIds);

    /** 按工单 ID 取计件工资明细行（employee_id/amount；表必须先经 countHrWageTable 确认存在） */
    List<WageRaw> selectWageDetails(@Param("workOrderId") Long workOrderId);

    /** 按科目编码取科目（结账凭证借贷科目解析；只取启用未删科目） */
    List<SubjectRaw> selectSubjects(@Param("codes") List<String> codes);

    /** 插入结账转账凭证（fin_voucher，DRAFT 状态，回写自增 id 到 param.id） */
    int insertVoucher(VoucherInsertParam param);

    /** 插入凭证分录（fin_voucher_entry，借贷单边金额） */
    int insertVoucherEntry(VoucherEntryInsertParam param);
}
