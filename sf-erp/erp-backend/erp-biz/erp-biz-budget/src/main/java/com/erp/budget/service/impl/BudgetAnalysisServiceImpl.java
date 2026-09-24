package com.erp.budget.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.budget.dto.vo.BudgetAnalysisRowVO;
import com.erp.budget.dto.vo.BudgetMonthlyCurveVO;
import com.erp.budget.entity.BudgetItem;
import com.erp.budget.entity.BudgetPlan;
import com.erp.budget.entity.BudgetUsageRecord;
import com.erp.budget.mapper.BudgetItemMapper;
import com.erp.budget.mapper.BudgetPlanMapper;
import com.erp.budget.mapper.BudgetUsageRecordMapper;
import com.erp.budget.mapper.CrossModuleMapper;
import com.erp.budget.service.BudgetAnalysisService;
import com.erp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预算执行分析 Service 实现。
 * <p>
 * 已用口径与 BudgetControlService.check 完全一致（凭证只读汇总 + 登记记录），
 * 保证"控制看到的数 = 分析看到的数"。取数一次性分组后在内存合并，
 * 避免 部门×科目×12月 逐格查询。
 */
@Service
@RequiredArgsConstructor
public class BudgetAnalysisServiceImpl implements BudgetAnalysisService {

    private final BudgetPlanMapper planMapper;
    private final BudgetItemMapper itemMapper;
    private final BudgetUsageRecordMapper usageMapper;
    private final CrossModuleMapper crossModuleMapper;

    @Override
    public List<BudgetAnalysisRowVO> analysis(Long planId, Long deptId) {
        BudgetPlan plan = requirePlan(planId);
        int year = plan.getBudgetYear();

        // 预算：该方案（可筛部门）按 部门×科目 汇总
        List<BudgetItem> items = itemMapper.selectList(Wrappers.lambdaQuery(BudgetItem.class)
                .eq(BudgetItem::getPlanId, planId)
                .eq(deptId != null, BudgetItem::getDeptId, deptId));
        Map<String, BigDecimal> budgetMap = new HashMap<>();
        Map<String, long[]> deptSubjectOf = new HashMap<>();
        for (BudgetItem cell : items) {
            String key = cell.getDeptId() + "-" + cell.getSubjectId();
            budgetMap.merge(key, cell.getAmount() == null ? BigDecimal.ZERO : cell.getAmount(),
                    BigDecimal::add);
            deptSubjectOf.put(key, new long[]{cell.getDeptId(), cell.getSubjectId()});
        }

        // 已用：凭证口径 + 登记口径，按 部门×科目 汇总
        Map<String, BigDecimal> usedMap = new HashMap<>();
        for (CrossModuleMapper.VoucherUsedRow row : crossModuleMapper.sumVoucherUsedGrouped(year, deptId)) {
            usedMap.merge(row.getDeptId() + "-" + row.getSubjectId(),
                    nvl(row.getAmount()), BigDecimal::add);
        }
        for (BudgetUsageRecord record : usageMapper.selectList(Wrappers.lambdaQuery(BudgetUsageRecord.class)
                .eq(BudgetUsageRecord::getYear, year)
                .eq(deptId != null, BudgetUsageRecord::getDeptId, deptId))) {
            usedMap.merge(record.getDeptId() + "-" + record.getSubjectId(),
                    nvl(record.getAmount()), BigDecimal::add);
        }

        Map<Long, String> deptNames = new HashMap<>();
        crossModuleMapper.listDepts().forEach(d -> deptNames.put(d.getId(), d.getName()));
        Map<Long, CrossModuleMapper.SubjectRef> subjects = new HashMap<>();
        crossModuleMapper.listExpenseSubjects().forEach(s -> subjects.put(s.getId(), s));

        List<BudgetAnalysisRowVO> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : budgetMap.entrySet()) {
            long[] ids = deptSubjectOf.get(entry.getKey());
            BigDecimal budget = entry.getValue();
            BigDecimal used = usedMap.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP);
            BudgetAnalysisRowVO row = new BudgetAnalysisRowVO();
            row.setDeptId(ids[0]);
            row.setDeptName(deptNames.getOrDefault(ids[0], "部门#" + ids[0]));
            row.setSubjectId(ids[1]);
            CrossModuleMapper.SubjectRef subject = subjects.get(ids[1]);
            if (subject == null) {
                CrossModuleMapper.SubjectRef fallback = crossModuleMapper.findSubject(ids[1]);
                subject = fallback == null ? new CrossModuleMapper.SubjectRef() : fallback;
            }
            row.setSubjectCode(subject.getCode());
            row.setSubjectName(subject.getName());
            row.setBudgetAmount(budget);
            row.setUsedAmount(used);
            row.setBalanceAmount(budget.subtract(used));
            row.setExecutionRate(rate(used, budget));
            rows.add(row);
        }
        rows.sort(Comparator.comparing(BudgetAnalysisRowVO::getDeptName)
                .thenComparing(r -> r.getSubjectCode() == null ? "" : r.getSubjectCode()));
        return rows;
    }

    @Override
    public List<BudgetMonthlyCurveVO> monthlyCurve(Long planId, Long deptId, Long subjectId) {
        BudgetPlan plan = requirePlan(planId);
        int year = plan.getBudgetYear();

        // 当月预算：方案明细按月份汇总（可筛部门/科目）
        List<BudgetItem> items = itemMapper.selectList(Wrappers.lambdaQuery(BudgetItem.class)
                .eq(BudgetItem::getPlanId, planId)
                .eq(deptId != null, BudgetItem::getDeptId, deptId)
                .eq(subjectId != null, BudgetItem::getSubjectId, subjectId));
        BigDecimal[] monthlyBudget = new BigDecimal[13];
        for (BudgetItem cell : items) {
            int m = cell.getMonth();
            monthlyBudget[m] = nvl(monthlyBudget[m]).add(nvl(cell.getAmount()));
        }

        // 当月已用：凭证口径（筛部门，科目在内存过滤）
        BigDecimal[] monthlyUsed = new BigDecimal[13];
        for (CrossModuleMapper.VoucherUsedRow row : crossModuleMapper.sumVoucherUsedGrouped(year, deptId)) {
            if (subjectId != null && !subjectId.equals(row.getSubjectId())) {
                continue;
            }
            monthlyUsed[row.getMonth()] = nvl(monthlyUsed[row.getMonth()]).add(nvl(row.getAmount()));
        }
        // 当月已用：登记口径
        for (BudgetUsageRecord record : usageMapper.selectList(Wrappers.lambdaQuery(BudgetUsageRecord.class)
                .eq(BudgetUsageRecord::getYear, year)
                .eq(deptId != null, BudgetUsageRecord::getDeptId, deptId)
                .eq(subjectId != null, BudgetUsageRecord::getSubjectId, subjectId))) {
            monthlyUsed[record.getMonth()] = nvl(monthlyUsed[record.getMonth()]).add(nvl(record.getAmount()));
        }

        List<BudgetMonthlyCurveVO> curve = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            BudgetMonthlyCurveVO point = new BudgetMonthlyCurveVO();
            point.setMonth(m);
            BigDecimal budget = nvl(monthlyBudget[m]).setScale(2, RoundingMode.HALF_UP);
            BigDecimal used = nvl(monthlyUsed[m]).setScale(2, RoundingMode.HALF_UP);
            point.setBudgetAmount(budget);
            point.setUsedAmount(used);
            point.setExecutionRate(rate(used, budget));
            curve.add(point);
        }
        return curve;
    }

    /** 加载方案，不存在抛业务异常 */
    private BudgetPlan requirePlan(Long id) {
        BudgetPlan plan = planMapper.selectById(id);
        if (plan == null) {
            throw BusinessException.ofKey("erp.budget.scheme-not-exists", id);
        }
        return plan;
    }

    /** null 安全（null 视为 0） */
    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** 执行率：已用/预算×100 保留 2 位；预算为 0 返回 null（前端展示 -） */
    private BigDecimal rate(BigDecimal used, BigDecimal budget) {
        if (budget == null || budget.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return used.multiply(new BigDecimal("100")).divide(budget, 2, RoundingMode.HALF_UP);
    }
}
