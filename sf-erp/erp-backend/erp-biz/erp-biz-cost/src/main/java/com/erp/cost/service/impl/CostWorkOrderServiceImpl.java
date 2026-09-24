package com.erp.cost.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.erp.common.exception.BusinessException;
import com.erp.common.result.PageResult;
import com.erp.cost.dto.form.OverheadForm;
import com.erp.cost.dto.raw.IssueItemRaw;
import com.erp.cost.dto.raw.WageRaw;
import com.erp.cost.dto.raw.WorkOrderRaw;
import com.erp.cost.dto.request.WorkOrderQuery;
import com.erp.cost.dto.vo.IssueCostLineVO;
import com.erp.cost.dto.vo.MaterialBalanceVO;
import com.erp.cost.dto.vo.WageLineVO;
import com.erp.cost.dto.vo.WorkOrderCostDetailVO;
import com.erp.cost.dto.vo.WorkOrderCostRowVO;
import com.erp.cost.entity.CostWorkOrder;
import com.erp.cost.mapper.CostIntegrationMapper;
import com.erp.cost.mapper.CostWorkOrderMapper;
import com.erp.cost.service.CostService;
import com.erp.cost.service.CostWorkOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工单成本归集服务实现（料工费三块）。
 * <ul>
 *   <li>料：mrp_material_issue(+item) 已审核领料实领量 × CostService 当前移动加权单价
 *       （历史时点价未接通实时计价前采用现行价，属口径约定，见模块文档）；</li>
 *   <li>工：hr_piece_wage_detail(work_order_id, employee_id, amount) —— HR 二期并行开发，
 *       约定表未建成前自动降级为 0 并在文档说明；</li>
 *   <li>费：cost_work_order.overhead_amount/labor_hours 手工分摊录入（整体覆盖）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CostWorkOrderServiceImpl implements CostWorkOrderService {

    /** hr_piece_wage_detail 存在性检查缓存（HR 建表后无需重启：每 60s 重探一次） */
    private static final long HR_PROBE_INTERVAL_MS = 60_000L;

    private final CostIntegrationMapper integrationMapper;
    private final CostWorkOrderMapper workOrderMapper;
    private final CostService costService;

    private volatile boolean hrTablePresent = false;
    private volatile long hrProbedAt = 0L;

    @Override
    public PageResult<WorkOrderCostRowVO> page(WorkOrderQuery query, long pageNum, long pageSize) {
        IPage<WorkOrderRaw> page = integrationMapper.selectWorkOrderPage(new Page<>(pageNum, pageSize),
                trim(query == null ? null : query.getKeyword()),
                trim(query == null ? null : query.getStatus()));
        List<Long> ids = page.getRecords().stream().map(WorkOrderRaw::getId).toList();
        Map<Long, BigDecimal> materialCosts = ids.isEmpty() ? Map.of() : materialCostByOrder(ids);
        Map<Long, BigDecimal> laborCosts = ids.isEmpty() ? Map.of() : wageTotalByOrder(ids);
        Map<Long, CostWorkOrder> stored = ids.isEmpty() ? Map.of() : storedByOrder(ids);

        List<WorkOrderCostRowVO> rows = page.getRecords().stream()
                .map(w -> assembleRow(w, materialCosts.getOrDefault(w.getId(), BigDecimal.ZERO),
                        laborCosts.getOrDefault(w.getId(), BigDecimal.ZERO), stored.get(w.getId())))
                .toList();
        return new PageResult<>(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public WorkOrderCostDetailVO detail(Long workOrderId) {
        WorkOrderRaw w = requireOrder(workOrderId);
        List<IssueCostLineVO> issueLines = issueLines(workOrderId);
        List<WageLineVO> wageLines = wageLines(workOrderId);
        CostWorkOrder stored = workOrderMapper.selectByWorkOrderId(workOrderId);

        BigDecimal materialCost = issueLines.stream()
                .map(IssueCostLineVO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal laborCost = wageLines.stream()
                .map(WageLineVO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overhead = stored == null ? BigDecimal.ZERO : nvl(stored.getOverheadAmount());
        BigDecimal total = materialCost.add(laborCost).add(overhead);

        return new WorkOrderCostDetailVO(w.getId(), w.getOrderNo(), w.getMaterialId(),
                w.getMaterialCode(), w.getMaterialName(), w.getQty(), w.getFinishQty(), w.getStatus(),
                w.getPlanStartDate(), w.getPlanEndDate(), issueLines, wageLines, overhead,
                stored == null ? BigDecimal.ZERO : nvl(stored.getLaborHours()),
                stored == null ? null : stored.getRemark(),
                materialCost, laborCost, overhead, total, unitCost(total, w));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveOverhead(Long workOrderId, OverheadForm form) {
        WorkOrderRaw w = requireOrder(workOrderId);
        CostWorkOrder row = workOrderMapper.selectByWorkOrderId(workOrderId);
        if (row == null) {
            row = new CostWorkOrder();
            row.setWorkOrderId(workOrderId);
        }
        // 快照字段每次录入都刷新，保证与 mrp_work_order 最新状态一致
        row.setOrderNo(w.getOrderNo());
        row.setMaterialId(w.getMaterialId());
        row.setQty(w.getQty());
        row.setFinishQty(w.getFinishQty());
        row.setStatus(w.getStatus());
        row.setLaborHours(form.getLaborHours());
        row.setOverheadAmount(form.getOverheadAmount());
        row.setRemark(form.getRemark());
        if (row.getId() == null) {
            workOrderMapper.insert(row);
        } else {
            workOrderMapper.updateById(row);
        }
        return row.getId();
    }

    // ===================== 归集子过程 =====================

    /** 料：领料明细 × 当前移动加权单价 */
    private List<IssueCostLineVO> issueLines(Long workOrderId) {
        return integrationMapper.selectIssueItems(List.of(workOrderId)).stream()
                .map(i -> {
                    MaterialBalanceVO bal = costService.currentBalance(i.getMaterialId());
                    BigDecimal amount = bal.unitCost().multiply(i.getQty())
                            .setScale(2, RoundingMode.HALF_UP);
                    return new IssueCostLineVO(i.getMaterialId(), i.getMaterialCode(),
                            i.getMaterialName(), i.getQty(), bal.unitCost(), amount);
                }).toList();
    }

    private Map<Long, BigDecimal> materialCostByOrder(List<Long> workOrderIds) {
        List<IssueItemRaw> items = integrationMapper.selectIssueItems(workOrderIds);
        // 批量取涉及物料的现行移动加权价，避免逐行查询
        Map<Long, BigDecimal> unitByMaterial = new HashMap<>();
        for (IssueItemRaw item : items) {
            unitByMaterial.computeIfAbsent(item.getMaterialId(),
                    id -> costService.currentBalance(id).unitCost());
        }
        Map<Long, BigDecimal> result = new HashMap<>();
        for (IssueItemRaw item : items) {
            BigDecimal unit = unitByMaterial.getOrDefault(item.getMaterialId(), BigDecimal.ZERO);
            BigDecimal amount = unit.multiply(item.getQty()).setScale(2, RoundingMode.HALF_UP);
            result.merge(item.getWorkOrderId(), amount, BigDecimal::add);
        }
        return result;
    }

    private Map<Long, BigDecimal> wageTotalByOrder(List<Long> workOrderIds) {
        Map<Long, BigDecimal> result = new HashMap<>();
        if (!hrWageTablePresent()) {
            return result; // HR 约定表未建成：工费降级为 0
        }
        for (WageRaw w : integrationMapper.selectWageTotals(workOrderIds)) {
            result.put(w.getWorkOrderId(), nvl(w.getAmount()));
        }
        return result;
    }

    private List<WageLineVO> wageLines(Long workOrderId) {
        if (!hrWageTablePresent()) {
            return List.of();
        }
        return integrationMapper.selectWageDetails(workOrderId).stream()
                .map(w -> new WageLineVO(w.getId(), w.getEmployeeId(), nvl(w.getAmount())))
                .toList();
    }

    private Map<Long, CostWorkOrder> storedByOrder(List<Long> workOrderIds) {
        return workOrderMapper.selectList(
                        com.baomidou.mybatisplus.core.toolkit.Wrappers.lambdaQuery(CostWorkOrder.class)
                                .in(CostWorkOrder::getWorkOrderId, workOrderIds))
                .stream().collect(Collectors.toMap(CostWorkOrder::getWorkOrderId, o -> o));
    }

    private WorkOrderCostRowVO assembleRow(WorkOrderRaw w, BigDecimal materialCost,
                                           BigDecimal laborCost, CostWorkOrder stored) {
        BigDecimal overhead = stored == null ? BigDecimal.ZERO : nvl(stored.getOverheadAmount());
        BigDecimal total = materialCost.add(laborCost).add(overhead);
        return new WorkOrderCostRowVO(w.getId(), w.getOrderNo(), w.getMaterialId(),
                w.getMaterialCode(), w.getMaterialName(), w.getQty(), w.getFinishQty(),
                w.getStatus(), materialCost, laborCost, overhead, total, unitCost(total, w));
    }

    /** 单位成本 = 总成本 / 完工数量（完工为 0 时按订单数量，订单数量也为 0 时返回 0） */
    private BigDecimal unitCost(BigDecimal total, WorkOrderRaw w) {
        BigDecimal base = w.getFinishQty() != null && w.getFinishQty().signum() > 0
                ? w.getFinishQty() : w.getQty();
        if (base == null || base.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return total.divide(base, 2, RoundingMode.HALF_UP);
    }

    private WorkOrderRaw requireOrder(Long workOrderId) {
        if (workOrderId == null) {
            throw BusinessException.ofKey("erp.cost.work-order-id-required");
        }
        List<WorkOrderRaw> rows = integrationMapper.selectWorkOrderById(workOrderId);
        if (rows.isEmpty()) {
            throw BusinessException.ofKey("erp.cost.production-order-not-exists", workOrderId);
        }
        return rows.get(0);
    }

    /**
     * hr_piece_wage_detail 是否已建成（60s 缓存重探，HR 建表后自动生效无需重启）。
     */
    private boolean hrWageTablePresent() {
        long now = System.currentTimeMillis();
        if (now - hrProbedAt < HR_PROBE_INTERVAL_MS) {
            return hrTablePresent;
        }
        synchronized (this) {
            if (now - hrProbedAt < HR_PROBE_INTERVAL_MS) {
                return hrTablePresent;
            }
            hrTablePresent = integrationMapper.countHrWageTable() > 0;
            hrProbedAt = now;
            return hrTablePresent;
        }
    }

    private static BigDecimal nvl(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
