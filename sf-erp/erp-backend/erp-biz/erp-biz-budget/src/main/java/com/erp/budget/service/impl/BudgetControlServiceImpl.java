package com.erp.budget.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.erp.budget.dto.form.BudgetUsageRecordForm;
import com.erp.budget.dto.vo.BudgetCheckResult;
import com.erp.budget.entity.BudgetControlConfig;
import com.erp.budget.entity.BudgetItem;
import com.erp.budget.entity.BudgetPlan;
import com.erp.budget.entity.BudgetUsageRecord;
import com.erp.budget.mapper.BudgetControlConfigMapper;
import com.erp.budget.mapper.BudgetItemMapper;
import com.erp.budget.mapper.BudgetPlanMapper;
import com.erp.budget.mapper.BudgetUsageRecordMapper;
import com.erp.budget.mapper.CrossModuleMapper;
import com.erp.budget.service.BudgetControlService;
import com.erp.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * 预算控制 Service 实现 —— 预算占用校验/执行数登记（跨模块核心能力）。
 * <p>
 * 口径与规则见 {@link BudgetControlService} 接口 Javadoc；关键实现约束：
 * <ul>
 *   <li>方案定位：同一年度多个已下达方案时取最新下达（id 最大）者；</li>
 *   <li>无方案/无预算额度：不控制放行（结果注明），避免阻断无预算科目的正常业务；</li>
 *   <li>BLOCK 场景抛 {@link BusinessException}，由全局异常处理器转统一错误响应。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class BudgetControlServiceImpl implements BudgetControlService {

    private final BudgetPlanMapper planMapper;
    private final BudgetItemMapper itemMapper;
    private final BudgetControlConfigMapper configMapper;
    private final BudgetUsageRecordMapper usageMapper;
    private final CrossModuleMapper crossModuleMapper;

    @Override
    public BudgetCheckResult check(Long deptId, Long subjectId, Integer year, Integer month, BigDecimal amount) {
        if (month == null || month < 1 || month > 12) {
            throw BusinessException.ofKey("erp.budget.check-month-invalid", month);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw BusinessException.ofKey("erp.budget.check-amount-positive");
        }
        CrossModuleMapper.DeptRef dept = crossModuleMapper.findDept(deptId);
        if (dept == null) {
            throw BusinessException.ofKey("erp.budget.check-dept-not-exists", deptId);
        }
        CrossModuleMapper.SubjectRef subject = crossModuleMapper.findSubject(subjectId);
        if (subject == null) {
            throw BusinessException.ofKey("erp.budget.check-account-not-exists", subjectId);
        }

        BudgetCheckResult result = new BudgetCheckResult();
        result.setDeptId(deptId);
        result.setDeptName(dept.getName());
        result.setSubjectId(subjectId);
        result.setSubjectCode(subject.getCode());
        result.setSubjectName(subject.getName());
        result.setYear(year);
        result.setMonth(month);
        result.setCheckAmount(amount.setScale(2, RoundingMode.HALF_UP));

        // 定位年度已下达方案（多个时取最新）；无方案不控制
        BudgetPlan plan = planMapper.selectOne(Wrappers.lambdaQuery(BudgetPlan.class)
                .eq(BudgetPlan::getBudgetYear, year)
                .eq(BudgetPlan::getStatus, BudgetPlan.STATUS_RELEASED)
                .orderByDesc(BudgetPlan::getId)
                .last("LIMIT 1"));
        if (plan == null) {
            result.setLevel("OK");
            result.setPassed(true);
            result.setBudgetAmount(BigDecimal.ZERO);
            result.setUsedAmount(BigDecimal.ZERO);
            result.setTotalAfterCheck(amount);
            result.setRatioPercent(null);
            result.setMessage(String.format("%d 年无已下达预算方案，不控制", year));
            return result;
        }
        BudgetControlConfig config = configOf(plan.getId());
        result.setControlWay(config.getControlWay());
        result.setThresholdPercent(config.getOverPercent());

        // 当月预算（uk 保证至多一行）
        BudgetItem cell = itemMapper.selectOne(Wrappers.lambdaQuery(BudgetItem.class)
                .eq(BudgetItem::getPlanId, plan.getId())
                .eq(BudgetItem::getDeptId, deptId)
                .eq(BudgetItem::getSubjectId, subjectId)
                .eq(BudgetItem::getMonth, month));
        BigDecimal budget = cell == null || cell.getAmount() == null ? BigDecimal.ZERO : cell.getAmount();
        result.setBudgetAmount(budget);
        BigDecimal used = getUsedAmount(deptId, subjectId, year, month);
        result.setUsedAmount(used);
        BigDecimal totalAfter = used.add(amount);
        result.setTotalAfterCheck(totalAfter);

        if (budget.compareTo(BigDecimal.ZERO) <= 0) {
            // 无预算额度不控制（放行），提示编制方补编预算
            result.setLevel("OK");
            result.setPassed(true);
            result.setRatioPercent(null);
            result.setMessage(String.format("部门[%s]科目[%s %s]%d-%02d 无预算额度，不控制",
                    dept.getName(), subject.getCode(), subject.getName(), year, month));
            return result;
        }

        BigDecimal ratio = totalAfter.multiply(new BigDecimal("100"))
                .divide(budget, 2, RoundingMode.HALF_UP);
        result.setRatioPercent(ratio);

        if (ratio.compareTo(config.getOverPercent()) < 0) {
            result.setLevel("OK");
            result.setPassed(true);
            result.setMessage(String.format("预算内：占用率 %s%%（阈值 %s%%）",
                    ratio.toPlainString(), config.getOverPercent().toPlainString()));
            return result;
        }

        // 超阈值：按控制方式裁决
        String detail = String.format(
                "预算超限：部门[%s]科目[%s %s]%d-%02d 预算 %s，已用 %s，本次 %s，占用率 %s%% ≥ 阈值 %s%%",
                dept.getName(), subject.getCode(), subject.getName(), year, month,
                budget.toPlainString(), used.toPlainString(), amount.toPlainString(),
                ratio.toPlainString(), config.getOverPercent().toPlainString());
        if (BudgetControlConfig.WAY_BLOCK.equals(config.getControlWay())) {
            // BLOCK：阻止业务（抛业务异常，前端按错误提示）
            result.setLevel("BLOCK");
            result.setPassed(false);
            throw BusinessException.ofKey("erp.budget.blocked-by-control", detail);
        }
        result.setLevel("WARN");
        result.setPassed(true);
        result.setMessage(detail + "（控制方式：警告放行）");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long record(BudgetUsageRecordForm form) {
        if (crossModuleMapper.findDept(form.getDeptId()) == null) {
            throw BusinessException.ofKey("erp.budget.actual-dept-not-exists", form.getDeptId());
        }
        CrossModuleMapper.SubjectRef subject = crossModuleMapper.findSubject(form.getSubjectId());
        if (subject == null) {
            throw BusinessException.ofKey("erp.budget.actual-account-not-exists", form.getSubjectId());
        }
        if (crossModuleMapper.countBudgetableSubject(form.getSubjectId()) == 0
                || crossModuleMapper.countChildSubjects(form.getSubjectId()) > 0) {
            throw BusinessException.ofKey("erp.budget.account-not-leaf-expense", subject.getCode(), subject.getName());
        }
        BudgetUsageRecord record = new BudgetUsageRecord();
        record.setDeptId(form.getDeptId());
        record.setSubjectId(form.getSubjectId());
        record.setYear(form.getYear());
        record.setMonth(form.getMonth());
        record.setAmount(form.getAmount());
        record.setSourceType(form.getSourceType() == null ? BudgetUsageRecord.SOURCE_MANUAL : form.getSourceType());
        record.setSourceId(form.getSourceId());
        record.setSourceNo(form.getSourceNo());
        record.setBizDate(form.getBizDate() == null
                ? java.time.LocalDate.of(form.getYear(), form.getMonth(), 1) : form.getBizDate());
        record.setRemark(form.getRemark());
        usageMapper.insert(record);
        return record.getId();
    }

    @Override
    public BigDecimal getUsedAmount(Long deptId, Long subjectId, Integer year, Integer month) {
        // 凭证口径：POSTED 凭证该科目借方，按制单人部门归属（约定表只读）
        BigDecimal voucher = crossModuleMapper.sumVoucherDebit(deptId, subjectId, year, month);
        // 登记口径：付款等推式记账（冲销负数自然抵减）
        List<BudgetUsageRecord> records = usageMapper.selectList(
                Wrappers.lambdaQuery(BudgetUsageRecord.class)
                        .eq(BudgetUsageRecord::getDeptId, deptId)
                        .eq(BudgetUsageRecord::getSubjectId, subjectId)
                        .eq(BudgetUsageRecord::getYear, year)
                        .eq(BudgetUsageRecord::getMonth, month)
                        .select(BudgetUsageRecord::getAmount));
        BigDecimal recorded = records.stream()
                .map(BudgetUsageRecord::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return voucher.add(recorded).setScale(2, RoundingMode.HALF_UP);
    }

    /** 方案控制配置（无配置给默认 WARN/100） */
    private BudgetControlConfig configOf(Long planId) {
        BudgetControlConfig config = configMapper.selectByPlanId(planId);
        if (config == null) {
            config = new BudgetControlConfig();
            config.setPlanId(planId);
            config.setControlWay(BudgetControlConfig.WAY_WARN);
            config.setOverPercent(BudgetControlConfig.DEFAULT_OVER_PERCENT);
        }
        return config;
    }
}
